package dev.ecalendar.domain.model

data class EditableEvent(
    val uid: String? = null,
    val title: String = "",
    val location: String? = null,
    val notes: String? = null,
    val url: String? = null,
    val startMillis: Long = System.currentTimeMillis(),
    val endMillis: Long = System.currentTimeMillis() + 3_600_000,
    val isAllDay: Boolean = false,
    val calendarSourceId: Long = 0,
    val rruleString: String? = null,
    val attendees: List<String> = emptyList(),
    val alarms: List<Int> = listOf(15),
    val colorHex: String? = null,
    val travelTimeMins: Int? = null,
    val originalIcs: String? = null,
)
