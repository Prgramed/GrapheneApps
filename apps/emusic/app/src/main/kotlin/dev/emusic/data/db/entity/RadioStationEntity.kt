package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String? = null,
    val homepage: String? = null,
    val favicon: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val tags: String? = null,
    val votes: Int = 0,
    val isHls: Boolean = false,
    val lastPlayedAt: Long? = null,
)
