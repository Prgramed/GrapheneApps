package com.prgramed.econtacts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.model.CardDavAccount
import com.prgramed.econtacts.domain.model.SyncResult
import com.prgramed.econtacts.domain.repository.CardDavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDavSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val addressBookPath: String = "",
    val isConfigured: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: Boolean? = null,
    val isSyncing: Boolean = false,
    val lastSyncResult: SyncResult? = null,
    val error: String? = null,
)

@HiltViewModel
class CardDavSettingsViewModel @Inject constructor(
    private val cardDavRepository: CardDavRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDavSettingsUiState())
    val uiState: StateFlow<CardDavSettingsUiState> = _uiState.asStateFlow()

    init {
        observeAccount()
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, testResult = null) }
    }

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, testResult = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, testResult = null) }
    }

    fun onAddressBookPathChanged(path: String) {
        _uiState.update { it.copy(addressBookPath = path, testResult = null) }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null, error = null) }
            try {
                val success = cardDavRepository.testConnection(
                    state.serverUrl, state.username, state.password,
                )
                if (success) {
                    _uiState.update { it.copy(isTesting = false, testResult = true) }
                    if (state.addressBookPath.isBlank()) {
                        val discovered = cardDavRepository.discover(
                            state.serverUrl, state.username, state.password,
                        )
                        if (discovered != null) {
                            _uiState.update { it.copy(addressBookPath = discovered) }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isTesting = false, testResult = false, error = "Connection failed. Check URL and credentials.") }
                }
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("EPERM") == true || e is SecurityException ->
                        "Network access denied. Go to Settings > Apps > eContacts and enable Network permission."
                    else -> "Connection error: ${e.message}"
                }
                _uiState.update { it.copy(isTesting = false, testResult = false, error = msg) }
            }
        }
    }

    fun saveAccount() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required") }
            return
        }
        viewModelScope.launch {
            cardDavRepository.saveAccount(
                CardDavAccount(state.serverUrl, state.username, state.addressBookPath),
                state.password,
            )
            _uiState.update { it.copy(isConfigured = true, error = null) }
        }
    }

    fun removeAccount() {
        viewModelScope.launch {
            cardDavRepository.removeAccount()
            _uiState.update {
                CardDavSettingsUiState()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            val result = cardDavRepository.sync()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncResult = result,
                    error = if (result.errors.isNotEmpty()) result.errors.first() else null,
                )
            }
        }
    }

    private fun observeAccount() {
        viewModelScope.launch {
            cardDavRepository.getAccount().collect { account ->
                if (account != null) {
                    _uiState.update {
                        it.copy(
                            serverUrl = account.serverUrl,
                            username = account.username,
                            addressBookPath = account.addressBookPath,
                            isConfigured = true,
                        )
                    }
                }
            }
        }
    }
}
