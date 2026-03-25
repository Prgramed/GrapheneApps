package dev.eweather.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.eweather.domain.model.AlertSeverity
import dev.eweather.domain.model.WeatherAlert

@Entity(
    tableName = "alerts",
    indices = [Index("locationId"), Index("expires")],
)
data class AlertEntity(
    @PrimaryKey val id: String,
    val locationId: Long,
    val event: String,
    val severity: String, // AlertSeverity.name
    val headline: String,
    val area: String,
    val effective: Long,
    val expires: Long,
)

fun AlertEntity.toDomain() = WeatherAlert(
    id = id,
    event = event,
    severity = runCatching { AlertSeverity.valueOf(severity) }.getOrDefault(AlertSeverity.MINOR),
    headline = headline,
    area = area,
    effective = effective,
    expires = expires,
)

fun WeatherAlert.toEntity(locationId: Long) = AlertEntity(
    id = id,
    locationId = locationId,
    event = event,
    severity = severity.name,
    headline = headline,
    area = area,
    effective = effective,
    expires = expires,
)
