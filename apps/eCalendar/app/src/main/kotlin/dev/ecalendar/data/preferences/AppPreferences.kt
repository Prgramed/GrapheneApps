package dev.ecalendar.data.preferences

enum class CalendarView {
    MONTH, WEEK, DAY, AGENDA,
}

data class AppPreferences(
    val activeView: CalendarView = CalendarView.MONTH,
    val activeDateMillis: Long = System.currentTimeMillis(),
    val defaultCalendarSourceId: Long = 0,
    val timeFormat24h: Boolean = true,
    val firstDayOfWeek: Int = 1, // 1=MON, 7=SUN
    val defaultReminderMins: Int = 15,
    val notificationsEnabled: Boolean = true,
    /** Minutes between auto-syncs. 0 = manual only. Floor is 15 (WorkManager minimum). */
    val syncIntervalMinutes: Int = 60,
    val themeMode: String = "system", // "system", "light", "dark"
)
