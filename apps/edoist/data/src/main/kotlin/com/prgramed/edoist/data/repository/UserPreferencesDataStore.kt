package com.prgramed.edoist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prgramed.edoist.domain.model.SortOrder
import com.prgramed.edoist.domain.repository.EDoistPreferences
import com.prgramed.edoist.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    private object Keys {
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val USERNAME = stringPreferencesKey("webdav_username")
        val PASSWORD = stringPreferencesKey("webdav_password")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")
        val SHOW_COMPLETED_TASKS = booleanPreferencesKey("show_completed_tasks")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = intPreferencesKey("theme_mode")
    }

    override fun getPreferences(): Flow<EDoistPreferences> = dataStore.data.map { prefs ->
        EDoistPreferences(
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            passwordEncrypted = prefs[Keys.PASSWORD] ?: "",
            syncEnabled = prefs[Keys.SYNC_ENABLED] ?: false,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 30,
            defaultSortOrder = prefs[Keys.DEFAULT_SORT_ORDER]
                ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                ?: SortOrder.MANUAL,
            showCompletedTasks = prefs[Keys.SHOW_COMPLETED_TASKS] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            themeMode = prefs[Keys.THEME_MODE] ?: 0,
        )
    }

    override suspend fun updateWebDavConfig(url: String, username: String, password: String) {
        dataStore.edit { prefs ->
            prefs[Keys.WEBDAV_URL] = url
            prefs[Keys.USERNAME] = username
            prefs[Keys.PASSWORD] = password
        }
    }

    override suspend fun updateSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_ENABLED] = enabled }
    }

    override suspend fun updateSyncInterval(minutes: Int) {
        dataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes }
    }

    override suspend fun updateDefaultSortOrder(sortOrder: SortOrder) {
        dataStore.edit { it[Keys.DEFAULT_SORT_ORDER] = sortOrder.name }
    }

    override suspend fun updateShowCompletedTasks(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_COMPLETED_TASKS] = show }
    }

    override suspend fun updateDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun updateThemeMode(mode: Int) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }
}
