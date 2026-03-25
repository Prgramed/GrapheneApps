package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.emusic.data.db.entity.CountryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryDao {

    @Upsert
    suspend fun upsertAll(countries: List<CountryEntity>)

    @Query("SELECT * FROM countries WHERE stationCount > 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CountryEntity>>

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun count(): Int
}
