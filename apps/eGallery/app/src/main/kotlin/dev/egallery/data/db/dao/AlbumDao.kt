package dev.egallery.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.egallery.data.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Insert
    suspend fun insert(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}
