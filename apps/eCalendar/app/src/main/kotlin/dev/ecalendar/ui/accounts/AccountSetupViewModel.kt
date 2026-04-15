package dev.ecalendar.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.caldav.DiscoveredCalendar
import dev.ecalendar.caldav.DiscoveryResult
import dev.ecalendar.data.CredentialStore
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
    private val credentialStore: CredentialStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * When non-zero, we're editing an existing account rather than creating a new one.
     * Supplied by the `accounts/edit/{accountId}` route.
     */
    val editingAccountId: Long = savedStateHandle.get<Long>("accountId")
        ?: savedStateHandle.get<String>("accountId")?.toLongOrNull() ?: 0L
    val isEditMode: Boolean = editingAccountId > 0

    private val _accountType = MutableStateFlow(AccountType.SYNOLOGY)
    val accountType: StateFlow<AccountType> = _accountType.asStateFlow()

    /** Zoho region — determines the CalDAV host (zoho.com vs zoho.eu etc). */
    private val _zohoRegion = MutableStateFlow(ZohoRegion.GLOBAL)
    val zohoRegion: StateFlow<ZohoRegion> = _zohoRegion.asStateFlow()

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

    init {
        if (isEditMode) {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = accountRepository.getById(editingAccountId) ?: return@launch
                _accountType.value = existing.type
                _displayName.value = existing.displayName
                _serverUrl.value = existing.baseUrl
                _username.value = existing.username
                _password.value = credentialStore.getPassword(existing.id) ?: ""
            }
        }
    }

    // Track whether URL / displayName were auto-populated (e.g. by the Zoho
    // email-derived default) so we can clear them cleanly when the user
    // switches account type. If the user hand-edited them we leave them alone.
    private var urlIsAutoPopulated = false
    private var displayNameIsAutoPopulated = false

    fun setAccountType(type: AccountType) {
        if (_accountType.value != type) {
            // Switching types: scrub auto-populated leftovers so the Zoho
            // "https://calendar.zoho.com/caldav/…" URL doesn't follow the user
            // into Synology or iCal setup.
            if (urlIsAutoPopulated) {
                _serverUrl.value = ""
                urlIsAutoPopulated = false
            }
            if (displayNameIsAutoPopulated) {
                _displayName.value = ""
                displayNameIsAutoPopulated = false
            }
        }
        _accountType.value = type
        _setupState.value = SetupState.Idle
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
        displayNameIsAutoPopulated = false
    }
    fun setServerUrl(url: String) {
        _serverUrl.value = url
        urlIsAutoPopulated = false
    }
    fun setUsername(user: String) {
        _username.value = user
        if (_accountType.value == AccountType.ZOHO && user.contains("@")) {
            rebuildZohoUrl()
            if (_displayName.value.isBlank()) {
                _displayName.value = "Zoho ($user)"
                displayNameIsAutoPopulated = true
            }
        }
    }
    fun setPassword(pass: String) { _password.value = pass }

    fun setZohoRegion(region: ZohoRegion) {
        _zohoRegion.value = region
        if (_accountType.value == AccountType.ZOHO && _username.value.contains("@")) {
            rebuildZohoUrl()
        }
    }

    private fun rebuildZohoUrl() {
        _serverUrl.value = "https://calendar.${_zohoRegion.value.host}/caldav/${_username.value}/"
        urlIsAutoPopulated = true
    }

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
            // Trailing slash semantics differ: CalDAV endpoints are collections
            // (need a slash), but iCal subscription URLs point to a specific
            // .ics file (appending a slash turns them into 404s — Google returns
            // 404 for `.../basic.ics/`).
            val rawUrl = _serverUrl.value.trim()
            val url = when {
                rawUrl.isEmpty() -> rawUrl
                type == AccountType.ICAL_SUBSCRIPTION -> rawUrl.trimEnd('/')
                !rawUrl.endsWith("/") -> "$rawUrl/"
                else -> rawUrl
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
                id = editingAccountId, // zero in create mode, real id in edit mode
                type = type,
                displayName = name,
                baseUrl = finalUrl,
                username = _username.value.trim(),
            )

            val password = _password.value

            val result = if (isEditMode) {
                accountRepository.updateAccount(account, password)
            } else {
                accountRepository.addAccount(account, password)
            }
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

/** Zoho data residency regions — each has its own CalDAV host. */
enum class ZohoRegion(val label: String, val host: String) {
    GLOBAL("Global (.com)", "zoho.com"),
    EU("Europe (.eu)", "zoho.eu"),
    INDIA("India (.in)", "zoho.in"),
    AUSTRALIA("Australia (.com.au)", "zoho.com.au"),
    JAPAN("Japan (.jp)", "zoho.jp"),
    CHINA("China (.com.cn)", "zoho.com.cn"),
}
