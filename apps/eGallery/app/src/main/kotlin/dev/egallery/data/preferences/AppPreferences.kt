package dev.egallery.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "egallery_preferences")

object PreferenceKeys {
    val ACTIVE_VIEW = stringPreferencesKey("active_view")
    val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
    val WIFI_ONLY_UPLOAD = booleanPreferencesKey("wifi_only_upload")
    val AUTO_EVICT_ENABLED = booleanPreferencesKey("auto_evict_enabled")
    val AUTO_UPLOAD_ENABLED = booleanPreferencesKey("auto_upload_enabled")
    val AUTO_DELETE_COVERS = booleanPreferencesKey("auto_delete_covers")
    val LAST_RECONCILE_AT = longPreferencesKey("last_reconcile_at")
    val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
    val UPLOAD_FOLDER_ID = intPreferencesKey("upload_folder_id")
    val LAST_DEVICE_SCAN_AT = longPreferencesKey("last_device_scan_at")
    val UPLOAD_CONCURRENCY = intPreferencesKey("upload_concurrency")
}
