package dev.eweather.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.eweather.data.db.entity.WeatherCacheEntity

@Dao
interface WeatherDao {

    @Upsert
    suspend fun upsertCache(entity: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE locationId = :locationId AND dataType = :dataType")
    suspend fun getCacheForLocation(locationId: Long, dataType: String): WeatherCacheEntity?

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThan")
    suspend fun deleteExpiredCache(olderThan: Long)
}
