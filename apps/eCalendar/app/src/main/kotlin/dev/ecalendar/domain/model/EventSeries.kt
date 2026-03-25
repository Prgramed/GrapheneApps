package dev.ecalendar.domain.model

data class EventSeries(
    val uid: String,
    val calendarSourceId: Long,
    val rawIcs: String,
    val etag: String = "",
    val serverUrl: String = "",
    val isLocal: Boolean = false,
)
