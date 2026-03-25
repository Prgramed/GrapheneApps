package dev.emusic.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.data.api.toDomain
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.TrackEntity
import dev.emusic.data.db.entity.toDomain
import dev.emusic.data.db.entity.toEntity
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.AlbumInfo
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.ArtistInfo
import dev.emusic.domain.model.Lyrics
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val api: SubsonicApiService,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val urlBuilder: SubsonicUrlBuilder,
) : LibraryRepository {

    // --- Sync ---

    override suspend fun syncArtists() {
        val response = api.getArtists().subsonicResponse
        val artists = response.artists?.index
            ?.flatMap { it.artist }
            ?.map { it.toDomain().toEntity() }
            ?: return
        artistDao.upsertAll(artists)
    }

    override suspend fun syncAlbums() {
        var offset = 0
        val pageSize = 500
        while (true) {
            val response = api.getAlbumList2(
                type = "alphabeticalByName",
                size = pageSize,
                offset = offset,
            ).subsonicResponse
            val albums = response.albumList2?.album
                ?.map { it.toDomain().toEntity() }
                ?: break
            if (albums.isEmpty()) break
            albumDao.upsertAll(albums)
            offset += albums.size
            if (albums.size < pageSize) break
        }
    }

    override suspend fun syncAlbumsIncremental(): Int {
        var offset = 0
        val pageSize = 100
        var newCount = 0
        while (true) {
            val response = api.getAlbumList2(
                type = "newest",
                size = pageSize,
                offset = offset,
            ).subsonicResponse
            val albums = response.albumList2?.album
                ?.map { it.toDomain().toEntity() }
                ?: break
            if (albums.isEmpty()) break

            // Check which albums are new (not in Room)
            val existingIds = albumDao.getExistingIds(albums.map { it.id }).toSet()
            val newAlbums = albums.filter { it.id !in existingIds }
            if (newAlbums.isNotEmpty()) {
                albumDao.upsertAll(newAlbums)
                newCount += newAlbums.size
            }

            // If we found existing albums in this batch, we've caught up
            if (newAlbums.size < albums.size) break
            if (albums.size < pageSize) break
            offset += pageSize
        }
        return newCount
    }

    override suspend fun syncAlbumTracks(albumId: String) {
        val response = api.getAlbum(albumId).subsonicResponse
        val tracks = response.album?.song
            ?.map { it.toDomain().toEntity() }
            ?: return
        upsertPreservingLocalPath(tracks)
    }

    override suspend fun syncAllTracks(onProgress: (current: Int, total: Int) -> Unit) {
        // Try search3 first (bulk fetch, much faster than album-by-album)
        var offset = 0
        val pageSize = 500
        var totalFetched = 0
        var search3Works = true

        while (search3Works) {
            try {
                val response = api.search3(
                    query = "",
                    artistCount = 0,
                    albumCount = 0,
                    songCount = pageSize,
                    songOffset = offset,
                ).subsonicResponse
                val songs = response.searchResult3?.song
                if (songs.isNullOrEmpty()) break

                val tracks = songs.map { it.toDomain().toEntity() }
                upsertPreservingLocalPath(tracks)
                totalFetched += tracks.size
                onProgress(totalFetched, totalFetched + pageSize)
                offset += songs.size
                if (songs.size < pageSize) break
            } catch (e: Exception) {
                timber.log.Timber.w(e, "search3 failed at offset $offset")
                if (totalFetched == 0) search3Works = false // search3 not supported, fall back
                break
            }
        }

        // Fallback: album-by-album if search3 didn't work
        if (!search3Works) {
            val albumIds = albumDao.getAllIds()
            for ((index, albumId) in albumIds.withIndex()) {
                for (attempt in 1..3) {
                    try {
                        syncAlbumTracks(albumId)
                        break
                    } catch (e: Exception) {
                        if (attempt == 3) {
                            timber.log.Timber.w(e, "Track sync failed for $albumId")
                        } else {
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                }
                onProgress(index + 1, albumIds.size)
            }
        }

    }

    // --- Observe ---

    override fun observeArtists(): Flow<PagingData<Artist>> =
        Pager(config = DEFAULT_PAGING_CONFIG) { artistDao.pagingAll() }
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeAlbums(): Flow<PagingData<Album>> =
        Pager(config = DEFAULT_PAGING_CONFIG) { albumDao.pagingAll() }
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeAlbumsSorted(sortSql: String, filterWhere: String): Flow<PagingData<Album>> {
        val where = if (filterWhere.isBlank()) "" else "WHERE $filterWhere"
        val sql = "SELECT * FROM albums $where ORDER BY $sortSql"
        return Pager(config = DEFAULT_PAGING_CONFIG) {
            albumDao.pagingRaw(SimpleSQLiteQuery(sql))
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override fun observeAllTracks(): Flow<PagingData<Track>> =
        Pager(config = DEFAULT_PAGING_CONFIG) { trackDao.pagingAll() }
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> =
        albumDao.observeByArtist(artistId).map { list -> list.map { it.toDomain() } }

    override fun observeAlbumsByGenre(genre: String): Flow<List<Album>> =
        albumDao.observeByGenre(genre).map { list -> list.map { it.toDomain() } }

    override fun observeTracksByAlbum(albumId: String): Flow<List<Track>> =
        trackDao.observeByAlbum(albumId).map { list -> list.map { it.toDomain() } }

    override fun searchTracks(query: String): Flow<List<Track>> =
        trackDao.searchFts(query).map { list -> list.map { it.toDomain() } }

    // --- Single item ---

    override suspend fun getTrack(id: String): Track? =
        trackDao.getById(id)?.toDomain()

    override suspend fun getAlbum(id: String): Album? =
        albumDao.getById(id)?.toDomain()

    override suspend fun getArtist(id: String): Artist? =
        artistDao.getById(id)?.toDomain()

    // --- Star (optimistic) ---

    override suspend fun starTrack(id: String) {
        trackDao.updateStarred(id, true)
        try {
            api.star(id = id)
        } catch (_: Exception) {
            trackDao.updateStarred(id, false)
        }
    }

    override suspend fun unstarTrack(id: String) {
        trackDao.updateStarred(id, false)
        try {
            api.unstar(id = id)
        } catch (_: Exception) {
            trackDao.updateStarred(id, true)
        }
    }

    // --- Rating ---

    override suspend fun setRating(id: String, rating: Int) {
        val previous = trackDao.getById(id)?.userRating
        trackDao.updateRating(id, if (rating == 0) null else rating)
        try {
            api.setRating(id, rating)
        } catch (_: Exception) {
            trackDao.updateRating(id, previous)
        }
    }

    // --- URL helpers ---

    override fun getCoverArtUrl(id: String, size: Int): String =
        urlBuilder.getCoverArtUrl(id, size)

    override fun getStreamUrl(id: String, maxBitRate: Int): String =
        urlBuilder.getStreamUrl(id, maxBitRate)

    // --- Info (cached in memory with 1-hour TTL) ---

    private data class CachedInfo<T>(val data: T, val fetchedAt: Long)
    private val artistInfoCache = java.util.concurrent.ConcurrentHashMap<String, CachedInfo<ArtistInfo>>()
    private val albumInfoCache = java.util.concurrent.ConcurrentHashMap<String, CachedInfo<AlbumInfo>>()
    private val infoCacheTtl = 60L * 60 * 1000 // 1 hour

    override suspend fun getArtistInfo(id: String): ArtistInfo? {
        val cached = artistInfoCache[id]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < infoCacheTtl) {
            return cached.data
        }
        return try {
            val info = api.getArtistInfo2(id).subsonicResponse.artistInfo2?.toDomain()
            if (info != null) artistInfoCache[id] = CachedInfo(info, System.currentTimeMillis())
            info
        } catch (_: Exception) {
            cached?.data // Return stale cache on error
        }
    }

    override suspend fun getAlbumInfo(id: String): AlbumInfo? {
        val cached = albumInfoCache[id]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < infoCacheTtl) {
            return cached.data
        }
        return try {
            val info = api.getAlbumInfo2(id).subsonicResponse.albumInfo?.toDomain()
            if (info != null) albumInfoCache[id] = CachedInfo(info, System.currentTimeMillis())
            info
        } catch (_: Exception) {
            cached?.data
        }
    }

    override suspend fun getTopSongs(artistName: String): List<Track> =
        try {
            api.getTopSongs(artistName).subsonicResponse.topSongs?.song
                ?.map { it.toDomain() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    override suspend fun getSimilarSongs(id: String, count: Int): List<Track> =
        try {
            api.getSimilarSongs2(id, count).subsonicResponse.similarSongs2?.song
                ?.map { it.toDomain() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    override suspend fun getRandomSongs(size: Int, genre: String?): List<Track> =
        try {
            api.getRandomSongs(size, genre).subsonicResponse.randomSongs?.song
                ?.map { it.toDomain() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    override suspend fun getLyrics(artist: String, title: String): Lyrics? =
        try {
            api.getLyrics(artist, title).subsonicResponse.lyrics?.toDomain()
        } catch (_: Exception) {
            null
        }

    private suspend fun upsertPreservingLocalPath(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val ids = tracks.map { it.id }
        val localPaths = trackDao.getLocalPaths(ids).associate { it.id to it.localPath }
        val merged = if (localPaths.isEmpty()) {
            tracks
        } else {
            tracks.map { t ->
                val existing = localPaths[t.id]
                if (existing != null) t.copy(localPath = existing) else t
            }
        }
        trackDao.upsertAll(merged)
    }

    companion object {
        private val DEFAULT_PAGING_CONFIG = PagingConfig(
            pageSize = 50,
            prefetchDistance = 100,
            enablePlaceholders = false,
        )
    }
}
