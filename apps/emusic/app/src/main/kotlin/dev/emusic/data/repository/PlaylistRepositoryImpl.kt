package dev.emusic.data.repository

import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.api.toDomain
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.PlaylistTrackCrossRef
import dev.emusic.data.db.entity.toDomain
import dev.emusic.data.db.entity.toEntity
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val api: SubsonicApiService,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
) : PlaylistRepository {

    override suspend fun syncPlaylists() {
        val response = api.getPlaylists().subsonicResponse
        val playlists = response.playlists?.playlist ?: return
        playlistDao.upsertAll(playlists.map { it.toDomain().toEntity() })

        // Strip playlists deleted from server
        val serverIds = playlists.mapNotNull { it.id }.toSet()
        val localIds = playlistDao.getAllIds()
        for (localId in localIds) {
            if (localId !in serverIds) {
                playlistDao.deleteById(localId)
            }
        }

        for (dto in playlists) {
            val id = dto.id ?: continue
            syncPlaylistTracks(id)
        }
    }

    override suspend fun refreshPlaylistTracks(playlistId: String) = syncPlaylistTracks(playlistId)

    private suspend fun syncPlaylistTracks(playlistId: String) {
        val response = api.getPlaylist(playlistId).subsonicResponse
        val trackDtos = response.playlist?.entry ?: return

        // Ensure tracks exist in the tracks table (JOIN requires them)
        val trackEntities = trackDtos.map { it.toDomain().toEntity() }
        // Preserve localPath for downloaded tracks (API doesn't include this field)
        val ids = trackEntities.map { it.id }
        val localPaths = trackDao.getLocalPaths(ids).associate { it.id to it.localPath }
        val merged = if (localPaths.isEmpty()) trackEntities else {
            trackEntities.map { t ->
                val existing = localPaths[t.id]
                if (existing != null) t.copy(localPath = existing) else t
            }
        }
        trackDao.upsertAll(merged)

        val refs = trackDtos.mapIndexed { index, trackDto ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackDto.id.orEmpty(),
                sortOrder = index,
            )
        }
        playlistDao.replacePlaylistTracks(playlistId, refs)
    }

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePlaylistTracks(playlistId: String): Flow<List<Track>> =
        playlistDao.observePlaylistTracks(playlistId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPlaylist(id: String): Playlist? =
        playlistDao.getById(id)?.toDomain()

    override suspend fun createPlaylist(name: String, trackIds: List<String>) {
        api.createPlaylist(name, trackIds.ifEmpty { null })
        syncPlaylists()
    }

    override suspend fun deletePlaylist(id: String) {
        api.deletePlaylist(id)
        playlistDao.deleteById(id)
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        api.updatePlaylist(playlistId = playlistId, songIdsToAdd = trackIds)
        syncPlaylistTracks(playlistId)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, index: Int) {
        api.updatePlaylist(playlistId = playlistId, songIndexesToRemove = listOf(index))
        syncPlaylistTracks(playlistId)
    }

    override suspend fun renamePlaylist(id: String, name: String) {
        api.updatePlaylist(playlistId = id, name = name)
        syncPlaylists()
    }

    override suspend fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) {
        // Optimistic local update
        val refs = trackIds.mapIndexed { index, trackId ->
            PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, sortOrder = index)
        }
        playlistDao.replacePlaylistTracks(playlistId, refs)

        // Subsonic API: remove all, then re-add in new order
        val playlist = api.getPlaylist(playlistId).subsonicResponse.playlist ?: return
        val currentCount = playlist.songCount ?: 0
        if (currentCount > 0) {
            val indicesToRemove = (0 until currentCount).toList()
            api.updatePlaylist(playlistId = playlistId, songIndexesToRemove = indicesToRemove)
        }
        api.updatePlaylist(playlistId = playlistId, songIdsToAdd = trackIds)
    }
}
