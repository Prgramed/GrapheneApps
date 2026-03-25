package dev.eweather.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.eweather.data.db.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Insert
    suspend fun insert(entity: LocationEntity): Long

    @Update
    suspend fun update(entity: LocationEntity)

    @Delete
    suspend fun delete(entity: LocationEntity)

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Query("SELECT * FROM locations WHERE isGps = 1 LIMIT 1")
    suspend fun getGpsLocation(): LocationEntity?

    @Query("SELECT * FROM locations ORDER BY sortOrder ASC")
    suspend fun getAll(): List<LocationEntity>
}
