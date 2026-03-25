package dev.ecalendar.domain.model

data class CalendarEvent(
    val id: Long = 0,
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
