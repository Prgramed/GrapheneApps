package com.prgramed.emessages.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.emessages.data.backup.BackupManager
import com.prgramed.emessages.data.backup.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val deliveryReportsEnabled: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val lastBackupTime: Long = 0,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val backupMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val backupManager: BackupManager,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    fun onNotificationsToggled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_NOTIFICATIONS] = enabled
            }
        }
    }

    fun onDeliveryReportsToggled(enabled: Boolean) {
        _uiState.update { it.copy(deliveryReportsEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_DELIVERY_REPORTS] = enabled
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            dataStore.data
                .catch { /* Use defaults on error */ }
                .collect { prefs ->
                    _uiState.update {
                        it.copy(
                            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                            deliveryReportsEnabled = prefs[KEY_DELIVERY_REPORTS] ?: false,
                            webDavUrl = prefs[KEY_WEBDAV_URL] ?: "",
                            webDavUsername = prefs[KEY_WEBDAV_USERNAME] ?: "",
                            webDavPassword = prefs[KEY_WEBDAV_PASSWORD] ?: "",
                            lastBackupTime = prefs[KEY_LAST_BACKUP] ?: 0L,
                        )
                    }
                }
        }
    }

    fun onWebDavUrlChanged(url: String) {
        _uiState.update { it.copy(webDavUrl = url) }
        viewModelScope.launch { dataStore.edit { it[KEY_WEBDAV_URL] = url } }
    }

    fun onWebDavUsernameChanged(username: String) {
        _uiState.update { it.copy(webDavUsername = username) }
        viewModelScope.launch { dataStore.edit { it[KEY_WEBDAV_USERNAME] = username } }
    }

    fun onWebDavPasswordChanged(password: String) {
        _uiState.update { it.copy(webDavPassword = password) }
        viewModelScope.launch {
            dataStore.edit { it[KEY_WEBDAV_PASSWORD] = password }
            scheduleAutoBackup()
        }
    }

    private fun scheduleAutoBackup() {
        val url = _uiState.value.webDavUrl
        if (url.isBlank()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately
            .build()

        workManager.enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun backupNow() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank() || state.isBackingUp) return
        _uiState.update { it.copy(isBackingUp = true, backupMessage = null) }
        viewModelScope.launch {
            val result = backupManager.createBackup(state.webDavUrl, state.webDavUsername, state.webDavPassword)
            result.onSuccess { count ->
                val now = System.currentTimeMillis()
                dataStore.edit { it[KEY_LAST_BACKUP] = now }
                _uiState.update { it.copy(isBackingUp = false, lastBackupTime = now, backupMessage = "Backed up $count messages") }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun restoreNow() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank() || state.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, backupMessage = "Starting restore...") }
        viewModelScope.launch {
            val result = backupManager.restore(
                state.webDavUrl, state.webDavUsername, state.webDavPassword,
                onProgress = { phase, _, _ -> _uiState.update { it.copy(backupMessage = phase) } },
            )
            result.onSuccess { count ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Restored $count messages") }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun forceRestoreSms() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank() || state.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, backupMessage = "Force restoring SMS...") }
        viewModelScope.launch {
            val result = backupManager.restore(
                state.webDavUrl, state.webDavUsername, state.webDavPassword,
                onProgress = { phase, _, _ -> _uiState.update { it.copy(backupMessage = phase) } },
                force = true,
                type = BackupManager.RestoreType.SMS_ONLY,
            )
            result.onSuccess { count ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Force restored $count SMS") }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun forceRestoreMms() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank() || state.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, backupMessage = "Force restoring MMS...") }
        viewModelScope.launch {
            val result = backupManager.restore(
                state.webDavUrl, state.webDavUsername, state.webDavPassword,
                onProgress = { phase, _, _ -> _uiState.update { it.copy(backupMessage = phase) } },
                force = true,
                type = BackupManager.RestoreType.MMS_ONLY,
            )
            result.onSuccess { count ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Force restored $count MMS") }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isRestoring = false, backupMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun deleteOldSms() {
        _uiState.update { it.copy(backupMessage = "Deleting old SMS...") }
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L // older than 24h
            val count = backupManager.deleteOldSms(cutoff)
            _uiState.update { it.copy(backupMessage = "Deleted $count old SMS") }
        }
    }

    companion object {
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_DELIVERY_REPORTS = booleanPreferencesKey("delivery_reports_enabled")
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_LAST_BACKUP = longPreferencesKey("last_backup_time")
    }
}
