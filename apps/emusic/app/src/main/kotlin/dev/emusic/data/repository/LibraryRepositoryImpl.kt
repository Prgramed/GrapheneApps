package dev.emusic.data.repository

import kotlinx.coroutines.ensureActive
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
        // Cleanup artists deleted from server
        val serverIds = artists.map { it.id }
        artistDao.deleteNotIn(serverIds)
    }

    override suspend fun syncAlbums() {
        var offset = 0
        val pageSize = 500
        val serverAlbumIds = mutableSetOf<String>()
        while (true) {
            val response = api.getAlbumList2(
                type = "alphabeticalByName",
                size = pageSize,
                offset = offset,
            ).subsonicResponse
            val albums = response.albumList2?.album
                ?.map { dto ->
                    val entity = dto.toDomain().toEntity()
                    val existing = albumDao.getById(entity.id)
                    if (existing != null) entity.copy(pinned = existing.pinned) else entity
                }
                ?: break
            if (albums.isEmpty()) break
            serverAlbumIds.addAll(albums.map { it.id })
            albumDao.upsertAll(albums)
            offset += albums.size
            if (albums.size < pageSize) break
        }
        // Cleanup albums deleted from server
        if (serverAlbumIds.isNotEmpty()) {
            albumDao.deleteNotIn(serverAlbumIds.toList())
        }
    }

    override suspend fun syncAlbumsIncremental(): List<String> {
        var offset = 0
        val pageSize = 100
        val newAlbumIds = mutableListOf<String>()
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

            val existingIds = albumDao.getExistingIds(albums.map { it.id }).toSet()
            val newAlbums = albums.filter { it.id !in existingIds }
            if (newAlbums.isNotEmpty()) {
                albumDao.upsertAll(newAlbums)
                newAlbumIds.addAll(newAlbums.map { it.id })
            }

            if (newAlbums.size < albums.size) break
            if (albums.size < pageSize) break
            offset += pageSize
        }
        return newAlbumIds
    }

    override suspend fun syncAlbumTracks(albumId: String) {
        val response = api.getAlbum(albumId).subsonicResponse
        val tracks = response.album?.song
            ?.map { it.toDomain().toEntity() }
            ?: return
        upsertPreservingLocalPath(tracks)
        // Remove local tracks that are no longer in the server's album response
        // (e.g. track deleted/moved on Navidrome). Without this, stale tracks
        // accumulate and show in the UI but fail to play.
        // Albums typically have <50 tracks, so no need to chunk the IN clause.
        val serverTrackIds = tracks.map { it.id }
        if (serverTrackIds.isNotEmpty()) {
            trackDao.deleteNotInAlbum(albumId, serverTrackIds)
        }
    }

    override suspend fun syncAllTracks(onProgress: (currentAlbum: Int, totalAlbums: Int) -> Unit) {
        // Album iteration is the only reliable full-library enumeration in Subsonic/Navidrome.
        // search3(query="") goes through the search index and returns incomplete results, so
        // we intentionally do NOT use it here. Orphan / non-album tracks are still covered:
        // Navidrome assigns every song to an album (synthesizing one if the file has no album
        // tag), and those synthetic albums appear in getAlbumList2 alongside real ones.
        val albumIds = albumDao.getAllIds()
        val failedAlbums = mutableListOf<String>()

        for ((index, albumId) in albumIds.withIndex()) {
            kotlin.coroutines.coroutineContext.ensureActive()
            var synced = false
            for (attempt in 1..3) {
                try {
                    syncAlbumTracks(albumId)
                    synced = true
                    break
                } catch (e: Exception) {
                    if (attempt == 3) {
                        timber.log.Timber.w(e, "Track sync failed for album $albumId (pass 1)")
                    } else {
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }
            }
            if (!synced) failedAlbums.add(albumId)
            onProgress(index + 1, albumIds.size)
        }

        // Second pass: retry albums that failed all 3 attempts, with longer backoff in case
        // the first pass hit a transient burst (rate limit, temporary network issue).
        if (failedAlbums.isNotEmpty()) {
            timber.log.Timber.w("Retrying ${failedAlbums.size} failed albums")
            for (albumId in failedAlbums.toList()) {
                kotlin.coroutines.coroutineContext.ensureActive()
                for (attempt in 1..3) {
                    try {
                        syncAlbumTracks(albumId)
                        failedAlbums.remove(albumId)
                        break
                    } catch (e: Exception) {
                        if (attempt == 3) {
                            timber.log.Timber.e(e, "Track sync permanently failed for album $albumId")
                        } else {
                            kotlinx.coroutines.delay(3000L * attempt)
                        }
                    }
                }
            }
        }

        // Backfill artists that only appear in tracks (not returned by getArtists)
        val trackOnlyArtists = trackDao.getTrackOnlyArtists()
        if (trackOnlyArtists.isNotEmpty()) {
            val entities = trackOnlyArtists.map { ref ->
                dev.emusic.data.db.entity.ArtistEntity(
                    id = ref.artistId,
                    name = ref.artist,
                    albumCount = 0,
                    starred = false,
                )
            }
            artistDao.upsertAll(entities)
        }

        // Completeness self-check: the sum of per-album trackCount reported by the server
        // should roughly equal our local track count. A large negative delta means some
        // albums were skipped or returned fewer tracks than advertised.
        val expected = albumDao.getTotalTrackCount()
        val actual = trackDao.count()
        if (expected > 0 && actual < expected - 5) {
            timber.log.Timber.w(
                "Track sync completeness mismatch: local=%d expected=%d (delta=%d, failedAlbums=%d)",
                actual, expected, expected - actual, failedAlbums.size,
            )
        } else {
            timber.log.Timber.d("Track sync complete: local=%d expected=%d", actual, expected)
        }
    }

    override suspend fun findStaleAlbums(): List<String> {
        // Compare the server-reported trackCount (stored in albums table) to the
        // actual local track count per album. Any mismatch means tracks were
        // added or removed on the server since the last full sync.
        val serverCounts = albumDao.getAllTrackCounts().associate { it.id to it.trackCount }
        val localCounts = trackDao.trackCountsByAlbum().associate { it.albumId to it.cnt }
        return serverCounts.filter { (albumId, expected) ->
            val actual = localCounts[albumId] ?: 0
            actual != expected
        }.keys.toList()
    }

    // --- Observe ---

    override fun observeArtists(): Flow<PagingData<Artist>> =
        Pager(config = DEFAULT_PAGING_CONFIG) { artistDao.pagingAll() }
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeArtistsSorted(sort: String): Flow<PagingData<Artist>> {
        val source = when (sort) {
            "ALBUM_COUNT" -> { { artistDao.pagingByAlbumCount() } }
            else -> { { artistDao.pagingAll() } }
        }
        return Pager(config = DEFAULT_PAGING_CONFIG, pagingSourceFactory = source)
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }
    }

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

    override fun observeTracksSorted(sort: String): Flow<PagingData<Track>> {
        val source = when (sort) {
            "ARTIST" -> { { trackDao.pagingByArtist() } }
            "ALBUM" -> { { trackDao.pagingByAlbum() } }
            "DURATION" -> { { trackDao.pagingByDuration() } }
            else -> { { trackDao.pagingAll() } }
        }
        return Pager(config = DEFAULT_PAGING_CONFIG, pagingSourceFactory = source)
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }
    }

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

    override suspend fun starAlbum(id: String) {
        albumDao.updateStarred(id, true)
        try { api.star(albumId = id) } catch (_: Exception) { albumDao.updateStarred(id, false) }
    }

    override suspend fun unstarAlbum(id: String) {
        albumDao.updateStarred(id, false)
        try { api.unstar(albumId = id) } catch (_: Exception) { albumDao.updateStarred(id, true) }
    }

    override suspend fun starArtist(id: String) {
        artistDao.updateStarred(id, true)
        try { api.star(artistId = id) } catch (_: Exception) { artistDao.updateStarred(id, false) }
    }

    override suspend fun unstarArtist(id: String) {
        artistDao.updateStarred(id, false)
        try { api.unstar(artistId = id) } catch (_: Exception) { artistDao.updateStarred(id, true) }
    }

    override suspend fun syncStarred() {
        try {
            val response = api.getStarred2()
            val starred = response.subsonicResponse.starred2 ?: return

            // Mark server-starred items as starred locally
            val starredTrackIds = mutableListOf<String>()
            for (track in starred.song) {
                val id = track.id ?: continue
                starredTrackIds.add(id)
                trackDao.updateStarred(id, true)
            }
            val starredAlbumIds = mutableListOf<String>()
            for (album in starred.album) {
                val id = album.id ?: continue
                starredAlbumIds.add(id)
                albumDao.updateStarred(id, true)
            }
            val starredArtistIds = mutableListOf<String>()
            for (artist in starred.artist) {
                val id = artist.id ?: continue
                starredArtistIds.add(id)
                artistDao.updateStarred(id, true)
            }

            // Two-way: clear starred flag on items NOT in the server's list.
            // Without this, un-starring in Navidrome web stays starred in the app.
            // Starred counts are typically small enough (<500) for a single IN clause.
            trackDao.clearStarredNotIn(starredTrackIds)
            albumDao.clearStarredNotIn(starredAlbumIds)
            artistDao.clearStarredNotIn(starredArtistIds)
        } catch (_: Exception) { }
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
    private val infoCacheMaxSize = 200 // prevent unbounded memory growth

    override suspend fun getArtistInfo(id: String): ArtistInfo? {
        val cached = artistInfoCache[id]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < infoCacheTtl) {
            return cached.data
        }
        return try {
            val info = api.getArtistInfo2(id).subsonicResponse.artistInfo2?.toDomain()
            if (info != null) {
                if (artistInfoCache.size >= infoCacheMaxSize) artistInfoCache.clear()
                artistInfoCache[id] = CachedInfo(info, System.currentTimeMillis())
            }
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
            if (info != null) {
                if (albumInfoCache.size >= infoCacheMaxSize) albumInfoCache.clear()
                albumInfoCache[id] = CachedInfo(info, System.currentTimeMillis())
            }
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
        // Batch to stay under SQLite's 999-variable limit
        val localPaths = tracks.map { it.id }
            .chunked(500)
            .flatMap { trackDao.getLocalPaths(it) }
            .associate { it.id to it.localPath }
        val merged = tracks.map { t ->
            val existing = localPaths[t.id]
            if (existing != null) t.copy(localPath = existing) else t
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
