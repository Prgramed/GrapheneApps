package com.prgramed.emessages.data.backup

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.map

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()
        val url = prefs[KEY_WEBDAV_URL] ?: return Result.failure()
        val username = prefs[KEY_WEBDAV_USERNAME] ?: ""
        val password = prefs[KEY_WEBDAV_PASSWORD] ?: ""

        if (url.isBlank()) return Result.failure()

        val result = backupManager.createBackup(url, username, password)
        return if (result.isSuccess) {
            dataStore.edit { it[KEY_LAST_BACKUP] = System.currentTimeMillis() }
            Log.d("BackupWorker", "Auto-backup completed: ${result.getOrNull()} messages")
            Result.success()
        } else {
            Log.e("BackupWorker", "Auto-backup failed", result.exceptionOrNull())
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "emessages_auto_backup"
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_LAST_BACKUP = longPreferencesKey("last_backup_time")
    }
}
