package dev.emusic.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import dev.emusic.data.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    fun pagingAll(): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, name COLLATE NOCASE")
    fun pagingByArtist(artistId: String): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, name COLLATE NOCASE")
    fun observeByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE starred = 1 ORDER BY name COLLATE NOCASE")
    fun observeStarred(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY year DESC, name COLLATE NOCASE LIMIT :limit")
    suspend fun getNewest(limit: Int = 10): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 10): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE starred = 1 LIMIT :limit")
    suspend fun getStarred(limit: Int = 10): List<AlbumEntity>

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM albums WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(trackCount), 0) FROM albums")
    suspend fun getTotalTrackCount(): Int

    @Query("SELECT id FROM albums")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM albums WHERE genre = :genre ORDER BY name COLLATE NOCASE")
    fun observeByGenre(genre: String): Flow<List<AlbumEntity>>

    @RawQuery(observedEntities = [AlbumEntity::class])
    fun pagingRaw(query: SupportSQLiteQuery): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, name COLLATE NOCASE")
    suspend fun getByArtistId(artistId: String): List<AlbumEntity>

    @Query("SELECT id FROM albums WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query("UPDATE albums SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("SELECT * FROM albums WHERE pinned = 1 ORDER BY name COLLATE NOCASE")
    fun observePinned(): Flow<List<AlbumEntity>>
}
