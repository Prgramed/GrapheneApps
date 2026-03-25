package dev.emusic.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val MAX_BITRATE = intPreferencesKey("max_bitrate")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val FORCE_OFFLINE_MODE = booleanPreferencesKey("force_offline_mode")
        val SCROBBLING_ENABLED = booleanPreferencesKey("scrobbling_enabled")
        val HEADS_UP_NOTIFICATIONS = booleanPreferencesKey("heads_up_notifications")
        val LAST_SYNC_MS = androidx.datastore.preferences.core.longPreferencesKey("last_sync_ms")
        val REPLAY_GAIN_MODE = intPreferencesKey("replay_gain_mode")
        val PRE_AMP_DB = androidx.datastore.preferences.core.floatPreferencesKey("pre_amp_db")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val EQ_BASS_BOOST = intPreferencesKey("eq_bass_boost")
        val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val EQ_ACTIVE_PRESET = stringPreferencesKey("eq_active_preset")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
    }

    val preferencesFlow: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            maxBitrate = prefs[Keys.MAX_BITRATE] ?: 0,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
            forceOfflineMode = prefs[Keys.FORCE_OFFLINE_MODE] ?: false,
            scrobblingEnabled = prefs[Keys.SCROBBLING_ENABLED] ?: true,
            headsUpNotificationsEnabled = prefs[Keys.HEADS_UP_NOTIFICATIONS] ?: true,
            replayGainMode = prefs[Keys.REPLAY_GAIN_MODE] ?: 3,
            preAmpDb = prefs[Keys.PRE_AMP_DB] ?: 0f,
            themeMode = prefs[Keys.THEME_MODE] ?: 0,
            equalizerEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqBandLevels = prefs[Keys.EQ_BAND_LEVELS] ?: "",
            eqBassBoost = prefs[Keys.EQ_BASS_BOOST] ?: 0,
            eqVirtualizer = prefs[Keys.EQ_VIRTUALIZER] ?: 0,
            eqActivePreset = prefs[Keys.EQ_ACTIVE_PRESET] ?: "Flat",
            crossfadeDurationMs = prefs[Keys.CROSSFADE_DURATION_MS] ?: 0,
            gaplessPlayback = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
        )
    }

    suspend fun updateServerUrl(url: String) {
        dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun updateUsername(username: String) {
        dataStore.edit { it[Keys.USERNAME] = username }
    }

    suspend fun updateMaxBitrate(bitrate: Int) {
        dataStore.edit { it[Keys.MAX_BITRATE] = bitrate }
    }

    suspend fun updateWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun updateForceOfflineMode(enabled: Boolean) {
        dataStore.edit { it[Keys.FORCE_OFFLINE_MODE] = enabled }
    }

    suspend fun updateScrobblingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SCROBBLING_ENABLED] = enabled }
    }

    suspend fun updateHeadsUpNotifications(enabled: Boolean) {
        dataStore.edit { it[Keys.HEADS_UP_NOTIFICATIONS] = enabled }
    }

    suspend fun getLastSyncMs(): Long {
        return dataStore.data.map { it[Keys.LAST_SYNC_MS] ?: 0L }.first()
    }

    suspend fun setLastSyncMs(ms: Long) {
        dataStore.edit { it[Keys.LAST_SYNC_MS] = ms }
    }

    suspend fun updateReplayGainMode(mode: Int) {
        dataStore.edit { it[Keys.REPLAY_GAIN_MODE] = mode }
    }

    suspend fun updatePreAmpDb(db: Float) {
        dataStore.edit { it[Keys.PRE_AMP_DB] = db }
    }

    suspend fun updateThemeMode(mode: Int) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun updateEqEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun updateEqBandLevels(levels: String) {
        dataStore.edit { it[Keys.EQ_BAND_LEVELS] = levels }
    }

    suspend fun updateEqBassBoost(strength: Int) {
        dataStore.edit { it[Keys.EQ_BASS_BOOST] = strength }
    }

    suspend fun updateEqVirtualizer(strength: Int) {
        dataStore.edit { it[Keys.EQ_VIRTUALIZER] = strength }
    }

    suspend fun updateEqActivePreset(name: String) {
        dataStore.edit { it[Keys.EQ_ACTIVE_PRESET] = name }
    }

    suspend fun updateCrossfadeDuration(ms: Int) {
        dataStore.edit { it[Keys.CROSSFADE_DURATION_MS] = ms }
    }

    suspend fun updateGaplessPlayback(enabled: Boolean) {
        dataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }
}
