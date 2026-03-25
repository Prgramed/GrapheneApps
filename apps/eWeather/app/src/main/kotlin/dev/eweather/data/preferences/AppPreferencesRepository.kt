package dev.eweather.data.preferences

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
                temperatureUnit = prefs[KEY_TEMP_UNIT]?.let {
                    runCatching { TemperatureUnit.valueOf(it) }.getOrDefault(TemperatureUnit.CELSIUS)
                } ?: TemperatureUnit.CELSIUS,
                windUnit = prefs[KEY_WIND_UNIT]?.let {
                    runCatching { WindUnit.valueOf(it) }.getOrDefault(WindUnit.KMH)
                } ?: WindUnit.KMH,
                refreshIntervalHours = prefs[KEY_REFRESH_INTERVAL] ?: 1,
                notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                activeLocationId = prefs[KEY_ACTIVE_LOCATION] ?: 0L,
            )
        }

    suspend fun updateTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[KEY_TEMP_UNIT] = unit.name }
    }

    suspend fun updateWindUnit(unit: WindUnit) {
        dataStore.edit { it[KEY_WIND_UNIT] = unit.name }
    }

    suspend fun updateRefreshInterval(hours: Int) {
        dataStore.edit { it[KEY_REFRESH_INTERVAL] = hours }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun updateActiveLocationId(id: Long) {
        dataStore.edit { it[KEY_ACTIVE_LOCATION] = id }
    }

    companion object {
        private val KEY_TEMP_UNIT = stringPreferencesKey("temperature_unit")
        private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_hours")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_ACTIVE_LOCATION = longPreferencesKey("active_location_id")
    }
}
