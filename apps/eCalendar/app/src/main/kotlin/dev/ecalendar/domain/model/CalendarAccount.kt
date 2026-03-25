package dev.ecalendar.domain.model

data class CalendarAccount(
    val id: Long = 0,
    val type: AccountType,
    val displayName: String,
    val baseUrl: String,
    val username: String = "",
    val colorHex: String = "#4285F4",
    val lastSyncedAt: Long? = null,
    val isEnabled: Boolean = true,
)
