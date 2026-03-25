package dev.ecalendar.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_series",
    indices = [Index("calendarSourceId")],
)
data class EventSeriesEntity(
    @PrimaryKey val uid: String,
    val calendarSourceId: Long,
    val rawIcs: String,
    val etag: String = "",
    val serverUrl: String = "",
    val isLocal: Boolean = false,
)
