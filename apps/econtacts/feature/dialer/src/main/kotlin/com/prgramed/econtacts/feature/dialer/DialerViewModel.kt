package com.prgramed.econtacts.feature.dialer

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SimInfo(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val phoneAccountHandle: PhoneAccountHandle?,
)

data class DialerUiState(
    val currentNumber: String = "",
    val matchedContactName: String? = null,
    val availableSims: List<SimInfo> = emptyList(),
    val showSimPicker: Boolean = false,
    val callPlaced: Boolean = false,
)

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val contentResolver: ContentResolver,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null

    fun onDigitPressed(digit: String) {
        _uiState.update { it.copy(currentNumber = it.currentNumber + digit) }
        lookupContact()
    }

    fun onBackspace() {
        _uiState.update { it.copy(currentNumber = it.currentNumber.dropLast(1)) }
        lookupContact()
    }

    fun onClear() {
        _uiState.update { it.copy(currentNumber = "", matchedContactName = null) }
        lookupJob?.cancel()
    }

    fun initiateCall() {
        val number = _uiState.value.currentNumber
        if (number.isBlank()) return
        viewModelScope.launch {
            val sims = withContext(Dispatchers.IO) { loadSimsInternal() }
            _uiState.update { it.copy(availableSims = sims) }
            if (sims.size >= 2) {
                _uiState.update { it.copy(showSimPicker = true) }
            } else {
                placeCallInternal(sims.firstOrNull()?.phoneAccountHandle)
            }
        }
    }

    fun selectSim(sim: SimInfo) {
        _uiState.update { it.copy(showSimPicker = false) }
        placeCallInternal(sim.phoneAccountHandle)
    }

    fun dismissSimPicker() {
        _uiState.update { it.copy(showSimPicker = false) }
    }

    fun onCallPlacedHandled() {
        _uiState.update { it.copy(callPlaced = false) }
    }

    @SuppressLint("MissingPermission")
    private fun placeCallInternal(accountHandle: PhoneAccountHandle?) {
        val number = _uiState.value.currentNumber
        if (number.isBlank()) return
        try {
            val telecomManager = application.getSystemService(TelecomManager::class.java) ?: return
            val uri = Uri.parse("tel:$number")
            val extras = Bundle()
            if (accountHandle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
            }
            telecomManager.placeCall(uri, extras)
            _uiState.update { it.copy(callPlaced = true) }
        } catch (_: Exception) {
            // Failed to place call
        }
    }

    private fun lookupContact() {
        lookupJob?.cancel()
        val number = _uiState.value.currentNumber
        if (number.length < 3) {
            _uiState.update { it.copy(matchedContactName = null) }
            return
        }
        lookupJob = viewModelScope.launch {
            delay(150) // debounce
            val name = withContext(Dispatchers.IO) { lookupByNumber(number) }
            _uiState.update { it.copy(matchedContactName = name) }
        }
    }

    private fun lookupByNumber(number: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            } else null
        }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun loadSimsInternal(): List<SimInfo> = try {
        val subManager = application.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()
        val telecomManager = application.getSystemService(TelecomManager::class.java)
            ?: return emptyList()
        val subscriptions = subManager.activeSubscriptionInfoList ?: emptyList()
        val phoneAccounts = try {
            telecomManager.callCapablePhoneAccounts
        } catch (_: SecurityException) {
            emptyList()
        }
        subscriptions.map { sub ->
            val handle = phoneAccounts.find { account ->
                account.id == sub.subscriptionId.toString() || account.id == sub.iccId
            }
            SimInfo(
                subscriptionId = sub.subscriptionId,
                displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                carrierName = sub.carrierName?.toString() ?: "",
                phoneAccountHandle = handle,
            )
        }
    } catch (_: SecurityException) {
        emptyList()
    }
}
