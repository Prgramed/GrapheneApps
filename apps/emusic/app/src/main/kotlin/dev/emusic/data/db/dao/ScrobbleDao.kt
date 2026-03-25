package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.emusic.data.db.entity.ScrobbleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrobbleDao {

    @Insert
    suspend fun insert(scrobble: ScrobbleEntity)

    @Query("SELECT COUNT(*) FROM scrobbles WHERE trackId = :trackId")
    suspend fun countByTrackId(trackId: String): Int

    @Query(
        """
        SELECT trackId FROM scrobbles
        WHERE timestamp >= :since
        GROUP BY trackId
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """,
    )
    fun getTopTrackIds(since: Long, limit: Int): Flow<List<String>>

    @Query(
        """
        SELECT trackId FROM scrobbles
        GROUP BY trackId
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """,
    )
    fun getTopTrackIdsAllTime(limit: Int): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT trackId FROM scrobbles
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun getRecentDistinctTrackIds(limit: Int): Flow<List<String>>

    @Query("SELECT COUNT(DISTINCT trackId) FROM scrobbles")
    fun getTotalUniqueTracksPlayed(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scrobbles")
    fun getTotalScrobbleCount(): Flow<Int>

    @Query(
        """
        SELECT DISTINCT t.albumId FROM scrobbles s
        INNER JOIN tracks t ON s.trackId = t.id
        ORDER BY s.timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentAlbumIds(limit: Int): List<String>

    @Query(
        """
        SELECT t.albumId, MAX(s.timestamp) as lastPlayed
        FROM scrobbles s INNER JOIN tracks t ON s.trackId = t.id
        GROUP BY t.albumId
        ORDER BY lastPlayed DESC
        LIMIT :limit
        """,
    )
    fun getRecentAlbumsFlow(limit: Int): Flow<List<RecentAlbumResult>>

    @Query(
        """
        SELECT t.artistId, MAX(s.timestamp) as lastPlayed
        FROM scrobbles s INNER JOIN tracks t ON s.trackId = t.id
        GROUP BY t.artistId
        ORDER BY lastPlayed DESC
        LIMIT :limit
        """,
    )
    fun getRecentArtistsFlow(limit: Int): Flow<List<RecentArtistResult>>

    @Query("SELECT * FROM scrobbles ORDER BY timestamp DESC LIMIT :limit")
    fun getAllScrobblesDesc(limit: Int): Flow<List<ScrobbleEntity>>

    @Query("DELETE FROM scrobbles")
    suspend fun deleteAll()

    // --- Stats queries ---

    @Query(
        """
        SELECT s.trackId, t.title, t.artist, t.albumId, COUNT(*) as count
        FROM scrobbles s INNER JOIN tracks t ON s.trackId = t.id
        WHERE s.timestamp >= :since
        GROUP BY s.trackId ORDER BY count DESC LIMIT :limit
        """,
    )
    suspend fun getTopTracksWithCount(since: Long, limit: Int): List<TrackPlayCount>

    @Query(
        """
        SELECT t.artistId, t.artist as name, COUNT(*) as count
        FROM scrobbles s INNER JOIN tracks t ON s.trackId = t.id
        WHERE s.timestamp >= :since
        GROUP BY t.artistId ORDER BY count DESC LIMIT :limit
        """,
    )
    suspend fun getTopArtists(since: Long, limit: Int): List<ArtistPlayCount>

    @Query(
        """
        SELECT t.albumId, a.name, a.artist, COUNT(*) as count
        FROM scrobbles s INNER JOIN tracks t ON s.trackId = t.id
        INNER JOIN albums a ON t.albumId = a.id
        WHERE s.timestamp >= :since
        GROUP BY t.albumId ORDER BY count DESC LIMIT :limit
        """,
    )
    suspend fun getTopAlbums(since: Long, limit: Int): List<AlbumPlayCount>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM scrobbles WHERE timestamp >= :since")
    suspend fun getTotalListeningTimeMs(since: Long): Long

    @Query(
        """
        SELECT (timestamp / 86400000) * 86400000 as dayTimestamp, SUM(durationMs) as totalMs
        FROM scrobbles WHERE timestamp >= :since
        GROUP BY dayTimestamp ORDER BY dayTimestamp ASC
        """,
    )
    suspend fun getDailyListening(since: Long): List<DailyListening>

    @Query(
        """
        SELECT t.genre, COUNT(*) as count FROM scrobbles s
        INNER JOIN tracks t ON s.trackId = t.id
        WHERE t.genre IS NOT NULL AND s.timestamp >= :since
        GROUP BY t.genre ORDER BY count DESC LIMIT :limit
        """,
    )
    suspend fun getTopGenres(since: Long, limit: Int): List<GenrePlayCount>

    @Query("SELECT DISTINCT timestamp / 86400000 as day FROM scrobbles ORDER BY day DESC")
    suspend fun getDistinctScrobbleDays(): List<Long>

    @Query("SELECT * FROM scrobbles ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstScrobble(): ScrobbleEntity?
}

data class TrackPlayCount(
    val trackId: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val count: Int,
)

data class ArtistPlayCount(
    val artistId: String,
    val name: String,
    val count: Int,
)

data class AlbumPlayCount(
    val albumId: String,
    val name: String,
    val artist: String,
    val count: Int,
)

data class DailyListening(
    val dayTimestamp: Long,
    val totalMs: Long,
)

data class GenrePlayCount(
    val genre: String,
    val count: Int,
)

data class RecentAlbumResult(
    val albumId: String,
    val lastPlayed: Long,
)

data class RecentArtistResult(
    val artistId: String,
    val lastPlayed: Long,
)
