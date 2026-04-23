package dev.emusic.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.emusic.data.db.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun pagingAll(): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists ORDER BY albumCount DESC, name COLLATE NOCASE")
    fun pagingByAlbumCount(): PagingSource<Int, ArtistEntity>

    @Query("DELETE FROM artists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM artists WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("SELECT id FROM artists")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM artists")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun count(): Int

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    suspend fun getAllSorted(): List<ArtistEntity>

    @Query("UPDATE artists SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)

    /** Clear starred flag on artists NOT in the server's starred list (two-way star sync). */
    @Query("UPDATE artists SET starred = 0 WHERE starred = 1 AND id NOT IN (:serverStarredIds)")
    suspend fun clearStarredNotIn(serverStarredIds: List<String>): Int
}
