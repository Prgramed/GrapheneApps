package com.prgramed.econtacts.call

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InCallUiState(
    val callerNumber: String = "",
    val callerName: String? = null,
    val callerPhotoUri: String? = null,
    val callState: Int = Call.STATE_DISCONNECTED,
    val callDurationSeconds: Long = 0,
    val isMuted: Boolean = false,
    val audioRoute: Int = android.telecom.CallAudioState.ROUTE_EARPIECE,
    val availableAudioRoutes: List<AudioRouteOption> = emptyList(),
    val showAudioRoutePicker: Boolean = false,
    val isOnHold: Boolean = false,
    val showDtmfPad: Boolean = false,
    val simLabel: String? = null,
    // RTT
    val isRttActive: Boolean = false,
    val showRttPanel: Boolean = false,
    val rttTranscript: List<RttMessage> = emptyList(),
    val pendingLocalText: String = "",
    val pendingRemoteText: String = "",
    val showRttRequestDialog: Boolean = false,
    val pendingRttRequestId: Int = -1,
    val rttError: String? = null,
    val canMerge: Boolean = false,
    val hasMultipleCalls: Boolean = false,
)

@HiltViewModel
class InCallViewModel @Inject constructor(
    private val contentResolver: ContentResolver,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InCallUiState())
    val uiState: StateFlow<InCallUiState> = _uiState.asStateFlow()

    init {
        collectCallState()
        collectAudioState()
        collectCallerInfo()
        collectRttState()
        collectCallCount()
        startTimer()
    }

    private fun collectCallState() {
        viewModelScope.launch {
            CallManager.callState.collect { state ->
                _uiState.update { it.copy(callState = state) }
            }
        }
        viewModelScope.launch {
            CallManager.isOnHold.collect { onHold ->
                _uiState.update { it.copy(isOnHold = onHold) }
            }
        }
    }

    private fun collectAudioState() {
        viewModelScope.launch {
            AudioRouteManager.isMuted.collect { muted ->
                _uiState.update { it.copy(isMuted = muted) }
            }
        }
        viewModelScope.launch {
            AudioRouteManager.audioRoute.collect { route ->
                _uiState.update { it.copy(audioRoute = route) }
            }
        }
        viewModelScope.launch {
            AudioRouteManager.availableRoutes.collect { routes ->
                _uiState.update { it.copy(availableAudioRoutes = routes) }
            }
        }
    }

    private fun collectCallerInfo() {
        viewModelScope.launch {
            CallManager.callerNumber.collect { number ->
                // Use pre-resolved info from CallService (works on lock screen)
                val preResolvedName = CallManager.callerName.value
                val preResolvedPhoto = CallManager.callerPhotoUri.value
                _uiState.update {
                    it.copy(
                        callerNumber = number,
                        callerName = preResolvedName,
                        callerPhotoUri = preResolvedPhoto,
                    )
                }
                if (number.isNotBlank() && preResolvedName == null) {
                    // Fallback: try resolving ourselves (may fail on lock screen)
                    resolveContact(number)
                }
                if (number.isNotBlank()) {
                    resolveSimLabel()
                }
            }
        }
        // Also collect updates to pre-resolved name (in case it arrives after number)
        viewModelScope.launch {
            CallManager.callerName.collect { name ->
                if (name != null) {
                    _uiState.update { it.copy(callerName = name) }
                }
            }
        }
        viewModelScope.launch {
            CallManager.callerPhotoUri.collect { photo ->
                if (photo != null) {
                    _uiState.update { it.copy(callerPhotoUri = photo) }
                }
            }
        }
    }

    private fun collectRttState() {
        viewModelScope.launch {
            CallManager.isRttActive.collect { active ->
                _uiState.update { it.copy(isRttActive = active, showRttPanel = active) }
            }
        }
        viewModelScope.launch {
            CallManager.rttMessages.collect { messages ->
                _uiState.update { it.copy(rttTranscript = messages) }
            }
        }
        viewModelScope.launch {
            CallManager.pendingRemoteText.collect { text ->
                _uiState.update { it.copy(pendingRemoteText = text) }
            }
        }
        viewModelScope.launch {
            CallManager.rttRequestEvent.collect { requestId ->
                _uiState.update {
                    it.copy(
                        showRttRequestDialog = requestId != null,
                        pendingRttRequestId = requestId ?: -1,
                    )
                }
            }
        }
        viewModelScope.launch {
            CallManager.rttError.collect { error ->
                _uiState.update { it.copy(rttError = error) }
            }
        }
    }

    private fun resolveContact(number: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number),
                    )
                    contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.PHOTO_URI,
                        ),
                        null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(
                                cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME),
                            )
                            val photo = cursor.getString(
                                cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI),
                            )
                            Pair(name, photo)
                        } else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.update {
                it.copy(
                    callerName = result?.first,
                    callerPhotoUri = result?.second,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveSimLabel() {
        viewModelScope.launch(Dispatchers.IO) {
            val handle = CallManager.getAccountHandle() ?: return@launch
            val label = try {
                val subManager = application.getSystemService(SubscriptionManager::class.java)
                    ?: return@launch
                val subs = subManager.activeSubscriptionInfoList ?: return@launch
                val matched = subs.find {
                    it.subscriptionId.toString() == handle.id || it.iccId == handle.id
                }
                matched?.displayName?.toString()
            } catch (_: SecurityException) {
                null
            }
            _uiState.update { it.copy(simLabel = label) }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            // Only tick while there's an active call
            CallManager.callState.collect { state ->
                if (state == android.telecom.Call.STATE_ACTIVE) {
                    while (CallManager.callState.value == android.telecom.Call.STATE_ACTIVE) {
                        val startTime = CallManager.callStartTime.value
                        val duration = if (startTime > 0) {
                            (System.currentTimeMillis() - startTime) / 1000
                        } else 0L
                        _uiState.update { it.copy(callDurationSeconds = duration) }
                        delay(1000)
                    }
                }
            }
        }
    }

    fun answer() = CallManager.answer()
    fun hangup() = CallManager.hangup()
    fun toggleHold() = CallManager.toggleHold()
    fun toggleMute() = AudioRouteManager.toggleMute()

    fun addCall() {
        CallManager.toggleHold()
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun mergeConference() = CallManager.mergeConference()
    fun swapConference() = CallManager.swapConference()

    private fun collectCallCount() {
        viewModelScope.launch {
            CallManager.calls.collect { calls ->
                _uiState.update {
                    it.copy(
                        hasMultipleCalls = calls.size >= 2,
                        canMerge = CallManager.canMerge(),
                    )
                }
            }
        }
    }

    fun showAudioRoutePicker() {
        val routes = _uiState.value.availableAudioRoutes
        if (routes.size <= 2) {
            // Toggle between the two routes directly
            val currentRoute = _uiState.value.audioRoute
            val otherRoute = routes.firstOrNull { it.route != currentRoute } ?: return
            AudioRouteManager.setRoute(otherRoute.route)
        } else {
            _uiState.update { it.copy(showAudioRoutePicker = true) }
        }
    }

    fun dismissAudioRoutePicker() {
        _uiState.update { it.copy(showAudioRoutePicker = false) }
    }

    fun selectAudioRoute(route: Int) {
        _uiState.update { it.copy(showAudioRoutePicker = false) }
        AudioRouteManager.setRoute(route)
    }

    fun toggleDtmfPad() {
        _uiState.update { it.copy(showDtmfPad = !it.showDtmfPad) }
    }

    fun onDtmfDigit(digit: Char) {
        CallManager.playDtmf(digit)
        viewModelScope.launch {
            delay(200)
            CallManager.stopDtmf()
        }
    }

    // RTT actions

    fun toggleRtt() {
        if (_uiState.value.isRttActive) {
            _uiState.update { it.copy(showRttPanel = !it.showRttPanel) }
        } else {
            CallManager.sendRttRequest()
        }
    }

    fun onRttTextChanged(text: String) {
        val oldText = _uiState.value.pendingLocalText
        _uiState.update { it.copy(pendingLocalText = text) }
        when {
            text.length > oldText.length -> {
                CallManager.writeRtt(text.substring(oldText.length))
            }
            text.length < oldText.length -> {
                val count = oldText.length - text.length
                CallManager.writeRtt("\b".repeat(count))
            }
        }
    }

    fun sendRttMessage() {
        val text = _uiState.value.pendingLocalText
        if (text.isBlank()) return
        CallManager.writeRtt("\n")
        CallManager.addLocalRttMessage(text)
        _uiState.update { it.copy(pendingLocalText = "") }
    }

    fun acceptRttRequest() {
        val id = _uiState.value.pendingRttRequestId
        if (id >= 0) CallManager.respondToRttRequest(id, true)
    }

    fun declineRttRequest() {
        val id = _uiState.value.pendingRttRequestId
        if (id >= 0) CallManager.respondToRttRequest(id, false)
    }

    fun dismissRttError() {
        _uiState.update { it.copy(rttError = null) }
    }
}
