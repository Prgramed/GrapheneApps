package com.grapheneapps.enotes.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SORT_ORDER = intPreferencesKey("sort_order")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_minutes")
        val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val LAST_SYNC = longPreferencesKey("last_sync_time")
    }

    val preferencesFlow: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            sortOrder = prefs[Keys.SORT_ORDER] ?: 0,
            themeMode = prefs[Keys.THEME_MODE] ?: 0,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL] ?: 0,
            autoLockMinutes = prefs[Keys.AUTO_LOCK] ?: 0,
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: "",
            webDavUsername = prefs[Keys.WEBDAV_USERNAME] ?: "",
            webDavPassword = prefs[Keys.WEBDAV_PASSWORD] ?: "",
            lastSyncTime = prefs[Keys.LAST_SYNC] ?: 0,
        )
    }

    suspend fun updateSortOrder(order: Int) {
        dataStore.edit { it[Keys.SORT_ORDER] = order }
    }

    suspend fun updateThemeMode(mode: Int) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun updateSyncInterval(minutes: Int) {
        dataStore.edit { it[Keys.SYNC_INTERVAL] = minutes }
    }

    suspend fun updateAutoLock(minutes: Int) {
        dataStore.edit { it[Keys.AUTO_LOCK] = minutes }
    }

    suspend fun updateWebDavUrl(url: String) { dataStore.edit { it[Keys.WEBDAV_URL] = url } }
    suspend fun updateWebDavUsername(u: String) { dataStore.edit { it[Keys.WEBDAV_USERNAME] = u } }
    suspend fun updateWebDavPassword(p: String) { dataStore.edit { it[Keys.WEBDAV_PASSWORD] = p } }
    suspend fun updateLastSyncTime(ts: Long) { dataStore.edit { it[Keys.LAST_SYNC] = ts } }
}
