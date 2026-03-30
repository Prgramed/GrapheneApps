package dev.egallery.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.egallery.data.db.entity.AlbumMediaEntity

@Dao
interface AlbumMediaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: AlbumMediaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<AlbumMediaEntity>)

    @Query("DELETE FROM album_media WHERE albumId = :albumId")
    suspend fun deleteByAlbum(albumId: String)

    @Query("DELETE FROM album_media WHERE nasId = :nasId")
    suspend fun deleteByNasId(nasId: String)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND nasId = :nasId")
    suspend fun deleteByAlbumAndNasId(albumId: String, nasId: String)
}
