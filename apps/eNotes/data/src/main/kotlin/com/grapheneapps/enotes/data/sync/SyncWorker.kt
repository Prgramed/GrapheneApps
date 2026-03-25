package com.grapheneapps.enotes.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()
        val url = prefs[KEY_WEBDAV_URL] ?: return Result.failure()
        val username = prefs[KEY_WEBDAV_USERNAME] ?: ""
        val password = prefs[KEY_WEBDAV_PASSWORD] ?: ""

        if (url.isBlank()) return Result.failure()

        val result = syncEngine.sync(url, username, password)
        return if (result.isSuccess) {
            dataStore.edit { it[KEY_LAST_SYNC] = System.currentTimeMillis() }
            Timber.d("Auto-sync completed: ${result.getOrNull()}")
            Result.success()
        } else {
            Timber.e("Auto-sync failed: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "enotes_auto_sync"
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_time")
    }
}
