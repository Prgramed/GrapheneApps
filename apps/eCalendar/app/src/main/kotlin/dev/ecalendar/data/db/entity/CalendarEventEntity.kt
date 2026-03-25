package dev.ecalendar.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [
        Index("instanceStart"),
        Index("instanceEnd"),
        Index("calendarSourceId"),
        Index("uid"),
    ],
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val instanceStart: Long,
    val instanceEnd: Long,
    val title: String,
    val location: String? = null,
    val notes: String? = null,
    val url: String? = null,
    val colorHex: String? = null,
    val isAllDay: Boolean = false,
    val calendarSourceId: Long,
    val recurrenceId: Long? = null,
    val isCancelled: Boolean = false,
    val travelTimeMins: Int? = null,
)
