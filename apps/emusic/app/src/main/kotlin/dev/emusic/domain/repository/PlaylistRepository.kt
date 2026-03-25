package dev.emusic.domain.repository

import dev.emusic.domain.model.Playlist
import dev.emusic.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    suspend fun syncPlaylists()

    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylistTracks(playlistId: String): Flow<List<Track>>

    suspend fun refreshPlaylistTracks(playlistId: String)

    suspend fun getPlaylist(id: String): Playlist?

    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList())

    suspend fun deletePlaylist(id: String)

    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>)

    suspend fun removeTrackFromPlaylist(playlistId: String, index: Int)

    suspend fun renamePlaylist(id: String, name: String)

    suspend fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>)
}
