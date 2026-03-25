package dev.eweather.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "weather_cache",
    primaryKeys = ["locationId", "dataType"],
    indices = [Index("locationId")],
)
data class WeatherCacheEntity(
    val locationId: Long,
    val dataType: String, // "forecast" or "air_quality"
    val json: String,
    val fetchedAt: Long,
)
