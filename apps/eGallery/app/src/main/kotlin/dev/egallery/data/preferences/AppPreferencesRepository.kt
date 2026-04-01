package dev.egallery.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val activeView: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_VIEW] ?: "TIMELINE"
    }

    val lastSyncAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.LAST_SYNC_AT] ?: 0L
    }

    val syncIntervalHours: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SYNC_INTERVAL_HOURS] ?: 6
    }

    val wifiOnlyUpload: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.WIFI_ONLY_UPLOAD] ?: true
    }

    val autoEvictEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.AUTO_EVICT_ENABLED] ?: true
    }

    val autoUploadEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.AUTO_UPLOAD_ENABLED] ?: true
    }

    suspend fun setAutoUploadEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.AUTO_UPLOAD_ENABLED] = enabled }
    }

    val autoDeleteCovers: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.AUTO_DELETE_COVERS] ?: true
    }

    suspend fun setAutoDeleteCovers(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.AUTO_DELETE_COVERS] = enabled }
    }

    suspend fun setActiveView(view: String) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_VIEW] = view }
    }

    suspend fun setLastSyncAt(timestamp: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_SYNC_AT] = timestamp }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        dataStore.edit { it[PreferenceKeys.SYNC_INTERVAL_HOURS] = hours }
    }

    suspend fun setWifiOnlyUpload(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.WIFI_ONLY_UPLOAD] = enabled }
    }

    suspend fun setAutoEvictEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.AUTO_EVICT_ENABLED] = enabled }
    }

    val lastReconcileAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.LAST_RECONCILE_AT] ?: 0L
    }

    suspend fun setLastReconcileAt(timestamp: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_RECONCILE_AT] = timestamp }
    }

    suspend fun getLastSyncAtOnce(): Long =
        dataStore.data.map { it[PreferenceKeys.LAST_SYNC_AT] ?: 0L }.first()

    suspend fun getLastReconcileAtOnce(): Long =
        dataStore.data.map { it[PreferenceKeys.LAST_RECONCILE_AT] ?: 0L }.first()

    val uploadFolderId: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.UPLOAD_FOLDER_ID] ?: 0
    }

    suspend fun setUploadFolderId(folderId: Int) {
        dataStore.edit { it[PreferenceKeys.UPLOAD_FOLDER_ID] = folderId }
    }

    suspend fun getUploadFolderIdOnce(): Int =
        dataStore.data.map { it[PreferenceKeys.UPLOAD_FOLDER_ID] ?: 0 }.first()

    val recentSearches: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[PreferenceKeys.RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\u001F").take(MAX_RECENT_SEARCHES)
    }

    suspend fun addRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val raw = prefs[PreferenceKeys.RECENT_SEARCHES] ?: ""
            val existing = if (raw.isBlank()) mutableListOf() else raw.split("\u001F").toMutableList()
            existing.remove(query)
            existing.add(0, query)
            prefs[PreferenceKeys.RECENT_SEARCHES] = existing.take(MAX_RECENT_SEARCHES).joinToString("\u001F")
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(PreferenceKeys.RECENT_SEARCHES) }
    }

    val firstLaunchDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.FIRST_LAUNCH_DONE] ?: false
    }

    suspend fun setFirstLaunchDone() {
        dataStore.edit { it[PreferenceKeys.FIRST_LAUNCH_DONE] = true }
    }

    suspend fun isFirstLaunchDone(): Boolean =
        dataStore.data.map { it[PreferenceKeys.FIRST_LAUNCH_DONE] ?: false }.first()

    suspend fun getLastDeviceScanAt(): Long =
        dataStore.data.map { it[PreferenceKeys.LAST_DEVICE_SCAN_AT] ?: 0L }.first()

    suspend fun setLastDeviceScanAt(timestamp: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_DEVICE_SCAN_AT] = timestamp }
    }

    val uploadConcurrency: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.UPLOAD_CONCURRENCY] ?: 3
    }

    suspend fun setUploadConcurrency(count: Int) {
        dataStore.edit { it[PreferenceKeys.UPLOAD_CONCURRENCY] = count.coerceIn(1, 8) }
    }

    suspend fun getUploadConcurrencyOnce(): Int =
        dataStore.data.map { it[PreferenceKeys.UPLOAD_CONCURRENCY] ?: 3 }.first()

    companion object {
        private const val MAX_RECENT_SEARCHES = 10
    }
}
