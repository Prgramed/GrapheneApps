package dev.ecalendar.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferencesFlow: Flow<AppPreferences> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            AppPreferences(
                activeView = prefs[KEY_ACTIVE_VIEW]?.let {
                    runCatching { CalendarView.valueOf(it) }.getOrDefault(CalendarView.MONTH)
                } ?: CalendarView.MONTH,
                activeDateMillis = prefs[KEY_ACTIVE_DATE] ?: System.currentTimeMillis(),
                defaultCalendarSourceId = prefs[KEY_DEFAULT_CALENDAR] ?: 0L,
                timeFormat24h = prefs[KEY_TIME_FORMAT_24H] ?: true,
                firstDayOfWeek = prefs[KEY_FIRST_DAY] ?: 1,
                defaultReminderMins = prefs[KEY_DEFAULT_REMINDER] ?: 15,
                notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                syncIntervalHours = prefs[KEY_SYNC_INTERVAL] ?: 1,
                themeMode = prefs[KEY_THEME_MODE] ?: "system",
            )
        }

    suspend fun updateActiveView(view: CalendarView) {
        dataStore.edit { it[KEY_ACTIVE_VIEW] = view.name }
    }

    suspend fun updateActiveDate(millis: Long) {
        dataStore.edit { it[KEY_ACTIVE_DATE] = millis }
    }

    suspend fun updateDefaultCalendar(id: Long) {
        dataStore.edit { it[KEY_DEFAULT_CALENDAR] = id }
    }

    suspend fun updateTimeFormat(is24h: Boolean) {
        dataStore.edit { it[KEY_TIME_FORMAT_24H] = is24h }
    }

    suspend fun updateFirstDayOfWeek(day: Int) {
        dataStore.edit { it[KEY_FIRST_DAY] = day }
    }

    suspend fun updateDefaultReminder(mins: Int) {
        dataStore.edit { it[KEY_DEFAULT_REMINDER] = mins }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun updateSyncInterval(hours: Int) {
        dataStore.edit { it[KEY_SYNC_INTERVAL] = hours }
    }

    suspend fun updateThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    companion object {
        private val KEY_ACTIVE_VIEW = stringPreferencesKey("active_view")
        private val KEY_ACTIVE_DATE = longPreferencesKey("active_date_millis")
        private val KEY_DEFAULT_CALENDAR = longPreferencesKey("default_calendar_source_id")
        private val KEY_TIME_FORMAT_24H = booleanPreferencesKey("time_format_24h")
        private val KEY_FIRST_DAY = intPreferencesKey("first_day_of_week")
        private val KEY_DEFAULT_REMINDER = intPreferencesKey("default_reminder_mins")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_SYNC_INTERVAL = intPreferencesKey("sync_interval_hours")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
