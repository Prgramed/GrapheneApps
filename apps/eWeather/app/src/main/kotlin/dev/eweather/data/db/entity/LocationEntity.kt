package dev.eweather.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.eweather.domain.model.SavedLocation

@Entity(
    tableName = "locations",
    indices = [Index("sortOrder"), Index("isGps")],
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val region: String = "",
    val country: String = "",
    val lat: Double,
    val lon: Double,
    val isGps: Boolean = false,
    val sortOrder: Int = 0,
)

fun LocationEntity.toDomain() = SavedLocation(
    id = id,
    name = name,
    region = region,
    country = country,
    lat = lat,
    lon = lon,
    isGps = isGps,
    sortOrder = sortOrder,
)

fun SavedLocation.toEntity() = LocationEntity(
    id = id,
    name = name,
    region = region,
    country = country,
    lat = lat,
    lon = lon,
    isGps = isGps,
    sortOrder = sortOrder,
)
