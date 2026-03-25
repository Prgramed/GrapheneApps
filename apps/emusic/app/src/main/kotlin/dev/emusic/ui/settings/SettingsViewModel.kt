package dev.emusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.data.preferences.CredentialStore
import dev.emusic.domain.usecase.SyncLibraryUseCase
import dev.emusic.domain.usecase.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, ERROR
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val credentialStore: CredentialStore,
    private val apiService: SubsonicApiService,
    private val syncLibraryUseCase: SyncLibraryUseCase,
) : ViewModel() {

    val serverUrl = MutableStateFlow("")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")
    val wifiOnlyDownloads = MutableStateFlow(true)
    val forceOfflineMode = MutableStateFlow(false)
    val maxBitrate = MutableStateFlow(0)
    val replayGainMode = MutableStateFlow(3) // OFF
    val preAmpDb = MutableStateFlow(0f)
    val themeMode = MutableStateFlow(0) // System
    val scrobblingEnabled = MutableStateFlow(true)
    val headsUpEnabled = MutableStateFlow(true)
    val crossfadeDuration = MutableStateFlow(0)
    val gaplessPlayback = MutableStateFlow(true)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val syncProgress: StateFlow<SyncProgress?> = syncLibraryUseCase.progress

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            serverUrl.value = prefs.serverUrl
            username.value = prefs.username
            password.value = credentialStore.password
            wifiOnlyDownloads.value = prefs.wifiOnlyDownloads
            forceOfflineMode.value = prefs.forceOfflineMode
            maxBitrate.value = prefs.maxBitrate
            replayGainMode.value = prefs.replayGainMode
            preAmpDb.value = prefs.preAmpDb
            themeMode.value = prefs.themeMode
            scrobblingEnabled.value = prefs.scrobblingEnabled
            headsUpEnabled.value = prefs.headsUpNotificationsEnabled
            crossfadeDuration.value = prefs.crossfadeDurationMs
            gaplessPlayback.value = prefs.gaplessPlayback
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.TESTING
            _errorMessage.value = null
            try {
                // Temporarily save to make the interceptor pick up credentials
                saveCredentials()
                val response = apiService.ping().subsonicResponse
                if (response.isOk) {
                    _connectionStatus.value = ConnectionStatus.SUCCESS
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _errorMessage.value = response.error?.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _errorMessage.value = e.message ?: "Connection failed"
            }
        }
    }

    fun save() {
        syncLibraryUseCase.activeJob = viewModelScope.launch {
            saveCredentials()
            syncLibraryUseCase().collect { }
        }
    }

    fun forceFullSync() {
        syncLibraryUseCase.activeJob = viewModelScope.launch {
            syncLibraryUseCase(forceFullSync = true).collect { }
        }
    }

    fun cancelSync() {
        syncLibraryUseCase.cancelSync()
    }

    fun updateWifiOnlyDownloads(enabled: Boolean) {
        wifiOnlyDownloads.value = enabled
        viewModelScope.launch { preferencesRepository.updateWifiOnlyDownloads(enabled) }
    }

    fun updateMaxBitrate(bitrate: Int) {
        maxBitrate.value = bitrate
        viewModelScope.launch { preferencesRepository.updateMaxBitrate(bitrate) }
    }

    fun updateForceOfflineMode(enabled: Boolean) {
        forceOfflineMode.value = enabled
        viewModelScope.launch { preferencesRepository.updateForceOfflineMode(enabled) }
    }

    fun updateReplayGainMode(mode: Int) {
        replayGainMode.value = mode
        viewModelScope.launch { preferencesRepository.updateReplayGainMode(mode) }
    }

    fun updatePreAmpDb(db: Float) {
        preAmpDb.value = db
        viewModelScope.launch { preferencesRepository.updatePreAmpDb(db) }
    }

    fun updateThemeMode(mode: Int) {
        themeMode.value = mode
        viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
    }

    fun updateScrobblingEnabled(enabled: Boolean) {
        scrobblingEnabled.value = enabled
        viewModelScope.launch { preferencesRepository.updateScrobblingEnabled(enabled) }
    }

    fun updateHeadsUpEnabled(enabled: Boolean) {
        headsUpEnabled.value = enabled
        viewModelScope.launch { preferencesRepository.updateHeadsUpNotifications(enabled) }
    }

    fun updateCrossfadeDuration(ms: Int) {
        crossfadeDuration.value = ms
        viewModelScope.launch { preferencesRepository.updateCrossfadeDuration(ms) }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        gaplessPlayback.value = enabled
        viewModelScope.launch { preferencesRepository.updateGaplessPlayback(enabled) }
    }

    private suspend fun saveCredentials() {
        preferencesRepository.updateServerUrl(serverUrl.value.trim())
        preferencesRepository.updateUsername(username.value.trim())
        credentialStore.password = password.value
    }
}
