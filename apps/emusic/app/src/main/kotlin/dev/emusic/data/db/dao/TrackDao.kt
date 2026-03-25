package dev.emusic.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import dev.emusic.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber")
    fun observeByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun pagingAll(): PagingSource<Int, TrackEntity>

    @Query("SELECT * FROM tracks WHERE starred = 1 ORDER BY title COLLATE NOCASE")
    fun observeStarred(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY title COLLATE NOCASE")
    fun observeDownloaded(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount = 0 ORDER BY title COLLATE NOCASE")
    fun observeNeverPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE starred = 1")
    fun observeStarredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE playCount = 0")
    fun observeNeverPlayedCount(): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT tracks.* FROM tracks
        JOIN tracks_fts ON tracks.rowid = tracks_fts.rowid
        WHERE tracks_fts MATCH :query
        """,
    )
    fun searchFts(query: String): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)

    @Query("UPDATE tracks SET userRating = :rating WHERE id = :id")
    suspend fun updateRating(id: String, rating: Int?)

    @Query("UPDATE tracks SET localPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: String, path: String?)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: String)

    @Query("UPDATE tracks SET localPath = NULL WHERE localPath IS NOT NULL")
    suspend fun clearAllLocalPaths()

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopByPlayCount(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE starred = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomStarred(limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY RANDOM() LIMIT :limit")
    suspend fun getLocalRandom(limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT genre, COUNT(*) as count FROM tracks
        WHERE genre IS NOT NULL AND genre != ''
        GROUP BY genre
        ORDER BY count DESC
        """,
    )
    fun observeGenresWithCount(): Flow<List<GenreCount>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber")
    suspend fun getByAlbumId(albumId: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(tracks: List<TrackEntity>)

    @Query("SELECT id, localPath FROM tracks WHERE id IN (:ids) AND localPath IS NOT NULL")
    suspend fun getLocalPaths(ids: List<String>): List<LocalPathEntry>
}

data class GenreCount(
    val genre: String,
    val count: Int,
)

data class LocalPathEntry(
    val id: String,
    val localPath: String,
)
