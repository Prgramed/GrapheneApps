package com.prgramed.eprayer.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prgramed.eprayer.domain.model.AdhanSound
import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.domain.model.UserPreferences
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val MANUAL_LATITUDE = doublePreferencesKey("manual_latitude")
        val MANUAL_LONGITUDE = doublePreferencesKey("manual_longitude")
        val MANUAL_CITY_NAME = stringPreferencesKey("manual_city_name")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val MADHAB = stringPreferencesKey("madhab")
        val ADHAN_SOUND = stringPreferencesKey("adhan_sound")
    }

    override fun getUserPreferences(): Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            calculationMethod = prefs[Keys.CALCULATION_METHOD]
                ?.let { runCatching { CalculationMethodType.valueOf(it) }.getOrNull() }
                ?: CalculationMethodType.MUSLIM_WORLD_LEAGUE,
            locationMode = prefs[Keys.LOCATION_MODE]
                ?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() }
                ?: LocationMode.GPS,
            manualLatitude = prefs[Keys.MANUAL_LATITUDE],
            manualLongitude = prefs[Keys.MANUAL_LONGITUDE],
            manualCityName = prefs[Keys.MANUAL_CITY_NAME],
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            madhab = prefs[Keys.MADHAB]
                ?.let { runCatching { MadhabType.valueOf(it) }.getOrNull() }
                ?: MadhabType.SHAFI,
            adhanSound = prefs[Keys.ADHAN_SOUND]
                ?.let { runCatching { AdhanSound.valueOf(it) }.getOrNull() }
                ?: AdhanSound.MOHAMMED_REFAAT,
        )
    }

    override suspend fun updateCalculationMethod(method: CalculationMethodType) {
        dataStore.edit { it[Keys.CALCULATION_METHOD] = method.name }
    }

    override suspend fun updateLocationMode(mode: LocationMode) {
        dataStore.edit { it[Keys.LOCATION_MODE] = mode.name }
    }

    override suspend fun updateManualLocation(
        latitude: Double,
        longitude: Double,
        cityName: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.MANUAL_LATITUDE] = latitude
            prefs[Keys.MANUAL_LONGITUDE] = longitude
            if (cityName != null) {
                prefs[Keys.MANUAL_CITY_NAME] = cityName
            } else {
                prefs.remove(Keys.MANUAL_CITY_NAME)
            }
        }
    }

    override suspend fun updateMadhab(madhab: MadhabType) {
        dataStore.edit { it[Keys.MADHAB] = madhab.name }
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun updateAdhanSound(sound: AdhanSound) {
        dataStore.edit { it[Keys.ADHAN_SOUND] = sound.name }
    }
}
