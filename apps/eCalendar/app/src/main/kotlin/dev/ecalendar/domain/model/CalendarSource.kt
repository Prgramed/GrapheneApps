package dev.ecalendar.domain.model

data class CalendarSource(
    val id: Long = 0,
    val accountId: Long,
    val calDavUrl: String,
    val displayName: String,
    val colorHex: String = "#4285F4",
    val ctag: String? = null,
    val isReadOnly: Boolean = false,
    val isVisible: Boolean = true,
    val isMirror: Boolean = false,
)
