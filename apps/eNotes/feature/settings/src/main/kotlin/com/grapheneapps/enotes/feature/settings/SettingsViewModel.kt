package com.grapheneapps.enotes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grapheneapps.enotes.data.preferences.AppPreferencesRepository
import com.grapheneapps.enotes.data.sync.SyncEngine
import com.grapheneapps.enotes.data.sync.WebDavClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val lastSyncTime: Long = 0,
    val syncIntervalMinutes: Int = 0,
    val isSyncing: Boolean = false,
    val isTesting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val joplinUrl: String = "",
    val joplinUsername: String = "",
    val joplinPassword: String = "",
    val importProgress: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val syncEngine: SyncEngine,
    private val webDavClient: WebDavClient,
    private val joplinImporter: com.grapheneapps.enotes.data.joplin.JoplinImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow
                .catch { }
                .collect { prefs ->
                    _uiState.update {
                        it.copy(
                            webDavUrl = prefs.webDavUrl,
                            webDavUsername = prefs.webDavUsername,
                            webDavPassword = prefs.webDavPassword,
                            lastSyncTime = prefs.lastSyncTime,
                            syncIntervalMinutes = prefs.syncIntervalMinutes,
                        )
                    }
                }
        }
    }

    fun saveCredentials(url: String, username: String, password: String) {
        viewModelScope.launch {
            preferencesRepository.updateWebDavUrl(url.trim())
            preferencesRepository.updateWebDavUsername(username.trim())
            preferencesRepository.updateWebDavPassword(password.trim())
            _uiState.update { it.copy(message = "Saved") }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank()) return
        _uiState.update { it.copy(isTesting = true, message = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = webDavClient.testConnection(state.webDavUrl, state.webDavUsername, state.webDavPassword)
            _uiState.update {
                it.copy(
                    isTesting = false,
                    message = if (result.isSuccess) "Connection successful" else "Failed: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    fun syncNow() {
        val state = _uiState.value
        if (state.webDavUrl.isBlank() || state.isSyncing) return
        _uiState.update { it.copy(isSyncing = true, message = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = syncEngine.sync(state.webDavUrl, state.webDavUsername, state.webDavPassword)
            result.onSuccess { syncResult ->
                preferencesRepository.updateLastSyncTime(System.currentTimeMillis())
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncTime = System.currentTimeMillis(),
                        message = "Synced: ${syncResult.uploaded}↑ ${syncResult.downloaded}↓ ${syncResult.conflicts} conflicts",
                    )
                }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(isSyncing = false, message = "Sync failed: ${e.message}") }
            }
        }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateSyncInterval(minutes)
            _uiState.update { it.copy(syncIntervalMinutes = minutes) }
        }
    }

    fun onJoplinUrlChanged(url: String) { _uiState.update { it.copy(joplinUrl = url) } }
    fun onJoplinUsernameChanged(u: String) { _uiState.update { it.copy(joplinUsername = u) } }
    fun onJoplinPasswordChanged(p: String) { _uiState.update { it.copy(joplinPassword = p) } }

    fun importFromJoplin() {
        val state = _uiState.value
        if (state.joplinUrl.isBlank() || state.isImporting) return
        _uiState.update { it.copy(isImporting = true, importProgress = "Starting…", message = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = joplinImporter.import(
                state.joplinUrl, state.joplinUsername, state.joplinPassword,
            ) { step, current, total ->
                val progress = if (total > 0) "$step ($current/$total)" else step
                _uiState.update { it.copy(importProgress = progress) }
            }
            result.onSuccess { importResult ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = null,
                        message = "Imported ${importResult.imported} notes, ${importResult.skippedEncrypted} encrypted skipped, ${importResult.errors} errors",
                    )
                }
            }
            result.onFailure { e ->
                _uiState.update {
                    it.copy(isImporting = false, importProgress = null, message = "Import failed: ${e.message}")
                }
            }
        }
    }
}
