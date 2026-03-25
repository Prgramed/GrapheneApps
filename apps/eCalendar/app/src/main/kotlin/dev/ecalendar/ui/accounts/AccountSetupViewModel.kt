package dev.ecalendar.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.caldav.DiscoveredCalendar
import dev.ecalendar.caldav.DiscoveryResult
import dev.ecalendar.domain.model.AccountType
import dev.ecalendar.domain.model.CalendarAccount
import dev.ecalendar.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _accountType = MutableStateFlow(AccountType.SYNOLOGY)
    val accountType: StateFlow<AccountType> = _accountType.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun setAccountType(type: AccountType) {
        _accountType.value = type
        _setupState.value = SetupState.Idle
    }

    fun setDisplayName(name: String) { _displayName.value = name }
    fun setServerUrl(url: String) { _serverUrl.value = url }
    fun setUsername(user: String) {
        _username.value = user
        // Auto-construct Zoho URL from email
        if (_accountType.value == AccountType.ZOHO && user.contains("@")) {
            _serverUrl.value = "https://calendar.zoho.com/caldav/$user/"
            if (_displayName.value.isBlank()) {
                _displayName.value = "Zoho ($user)"
            }
        }
    }
    fun setPassword(pass: String) { _password.value = pass }

    fun verifyAndSave() {
        viewModelScope.launch(Dispatchers.IO) {
            _setupState.value = SetupState.Verifying

            val type = _accountType.value
            val name = _displayName.value.ifBlank {
                when (type) {
                    AccountType.SYNOLOGY -> "Synology"
                    AccountType.ZOHO -> "Zoho"
                    AccountType.ICAL_SUBSCRIPTION -> "iCal Subscription"
                }
            }
            val url = _serverUrl.value.trim().let {
                if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it
            }

            if (url.isBlank()) {
                _setupState.value = SetupState.Error("URL is required")
                return@launch
            }

            // For iCal subscriptions, convert webcal:// to https://
            val finalUrl = if (type == AccountType.ICAL_SUBSCRIPTION) {
                url.replace("webcal://", "https://")
            } else url

            val account = CalendarAccount(
                type = type,
                displayName = name,
                baseUrl = finalUrl,
                username = _username.value.trim(),
            )

            val password = _password.value

            val result = accountRepository.addAccount(account, password)
            _setupState.value = when (result) {
                is DiscoveryResult.Success -> {
                    if (type == AccountType.ICAL_SUBSCRIPTION) {
                        SetupState.Success(listOf(DiscoveredCalendar(finalUrl, name, null, false)))
                    } else {
                        SetupState.Success(result.calendars)
                    }
                }
                is DiscoveryResult.AuthFailed -> SetupState.Error("Authentication failed. Check your credentials.")
                is DiscoveryResult.NotCalDav -> SetupState.Error("No CalDAV calendars found at this URL.")
                is DiscoveryResult.NetworkError -> SetupState.Error("Network error: ${result.message}")
            }
        }
    }
}

sealed class SetupState {
    data object Idle : SetupState()
    data object Verifying : SetupState()
    data class Success(val calendars: List<DiscoveredCalendar>) : SetupState()
    data class Error(val message: String) : SetupState()
}
