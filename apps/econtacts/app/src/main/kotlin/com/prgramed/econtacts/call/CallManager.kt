package com.prgramed.econtacts.call

import android.telecom.Call
import android.telecom.VideoProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RttMessage(
    val text: String,
    val isRemote: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

object CallManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private val _callStartTime = MutableStateFlow(0L)
    val callStartTime: StateFlow<Long> = _callStartTime.asStateFlow()

    private val _callerNumber = MutableStateFlow("")
    val callerNumber: StateFlow<String> = _callerNumber.asStateFlow()

    // Pre-resolved caller info from CallService (works on lock screen)
    private val _callerName = MutableStateFlow<String?>(null)
    val callerName: StateFlow<String?> = _callerName.asStateFlow()

    private val _callerPhotoUri = MutableStateFlow<String?>(null)
    val callerPhotoUri: StateFlow<String?> = _callerPhotoUri.asStateFlow()

    fun setCallerInfo(name: String?, photoUri: String?) {
        _callerName.value = name
        _callerPhotoUri.value = photoUri
    }

    // RTT state
    private val _isRttActive = MutableStateFlow(false)
    val isRttActive: StateFlow<Boolean> = _isRttActive.asStateFlow()

    private val _rttMessages = MutableStateFlow<List<RttMessage>>(emptyList())
    val rttMessages: StateFlow<List<RttMessage>> = _rttMessages.asStateFlow()

    private val _pendingRemoteText = MutableStateFlow("")
    val pendingRemoteText: StateFlow<String> = _pendingRemoteText.asStateFlow()

    private val _rttRequestEvent = MutableStateFlow<Int?>(null)
    val rttRequestEvent: StateFlow<Int?> = _rttRequestEvent.asStateFlow()

    private val _rttError = MutableStateFlow<String?>(null)
    val rttError: StateFlow<String?> = _rttError.asStateFlow()

    private var primaryCall: Call? = null
    private var currentRttCall: Call.RttCall? = null
    private var rttReaderJob: Job? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            _callState.value = state
            _isOnHold.value = state == Call.STATE_HOLDING
            if (state == Call.STATE_ACTIVE && _callStartTime.value == 0L) {
                _callStartTime.value = System.currentTimeMillis()
            }
            // Retry capturing number if we missed it initially
            if (_callerNumber.value.isBlank()) {
                val number = call.details?.handle?.schemeSpecificPart ?: ""
                if (number.isNotBlank()) {
                    _callerNumber.value = number
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            // Details may arrive after the call is added — capture number if still missing
            if (_callerNumber.value.isBlank()) {
                val number = details.handle?.schemeSpecificPart ?: ""
                if (number.isNotBlank()) {
                    _callerNumber.value = number
                }
            }
        }

        override fun onRttStatusChanged(call: Call, enabled: Boolean, rttCall: Call.RttCall?) {
            _isRttActive.value = enabled
            if (enabled && rttCall != null) {
                startRttReader(rttCall)
            } else {
                stopRttReader()
            }
        }

        override fun onRttRequest(call: Call, id: Int) {
            _rttRequestEvent.value = id
        }

        override fun onRttInitiationFailure(call: Call, reason: Int) {
            _isRttActive.value = false
            _rttError.value = "RTT is not supported for this call"
        }
    }

    fun addCall(call: Call) {
        _calls.value = _calls.value + call
        setPrimary(call)
    }

    fun removeCall(call: Call) {
        call.unregisterCallback(callback)
        _calls.value = _calls.value - call
        if (primaryCall == call) {
            val remaining = _calls.value.firstOrNull()
            if (remaining != null) {
                setPrimary(remaining)
            } else {
                resetState()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setPrimary(call: Call) {
        primaryCall?.unregisterCallback(callback)
        primaryCall = call
        call.registerCallback(callback)
        _callState.value = call.state
        _isOnHold.value = call.state == Call.STATE_HOLDING
        _callStartTime.value =
            if (call.state == Call.STATE_ACTIVE) System.currentTimeMillis() else 0L

        // Try multiple ways to get the caller number
        val number = call.details?.handle?.schemeSpecificPart
            ?: call.details?.gatewayInfo?.originalAddress?.schemeSpecificPart
            ?: ""
        _callerNumber.value = number

        // Check if call already has RTT
        val rttCall = call.rttCall
        if (rttCall != null) {
            _isRttActive.value = true
            startRttReader(rttCall)
        }
    }

    private fun resetState() {
        primaryCall = null
        _callState.value = Call.STATE_DISCONNECTED
        _isOnHold.value = false
        _callStartTime.value = 0L
        _callerNumber.value = ""
        _callerName.value = null
        _callerPhotoUri.value = null
        stopRttReader()
        _isRttActive.value = false
        _rttMessages.value = emptyList()
        _pendingRemoteText.value = ""
        _rttRequestEvent.value = null
        _rttError.value = null
    }

    fun answer() {
        primaryCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        primaryCall?.disconnect()
    }

    fun toggleHold() {
        val call = primaryCall ?: return
        if (_isOnHold.value) call.unhold() else call.hold()
    }

    fun playDtmf(digit: Char) {
        primaryCall?.playDtmfTone(digit)
    }

    fun stopDtmf() {
        primaryCall?.stopDtmfTone()
    }

    fun getAccountHandle() = primaryCall?.details?.accountHandle

    fun canMerge(): Boolean {
        val calls = _calls.value
        if (calls.size < 2) return false
        return calls.any { call ->
            val capabilities = call.details?.callCapabilities ?: 0
            capabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0
        }
    }

    fun mergeConference() {
        val calls = _calls.value
        if (calls.size < 2) return
        val mergeable = calls.firstOrNull { call ->
            val capabilities = call.details?.callCapabilities ?: 0
            capabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0
        } ?: return
        mergeable.mergeConference()
    }

    fun swapConference() {
        val calls = _calls.value
        if (calls.size < 2) return
        val swappable = calls.firstOrNull { call ->
            val capabilities = call.details?.callCapabilities ?: 0
            capabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE != 0
        } ?: return
        swappable.swapConference()
    }

    // RTT methods

    fun sendRttRequest() {
        primaryCall?.sendRttRequest()
    }

    fun respondToRttRequest(id: Int, accept: Boolean) {
        primaryCall?.respondToRttRequest(id, accept)
        _rttRequestEvent.value = null
    }

    fun writeRtt(text: String) {
        currentRttCall?.write(text)
    }

    fun addLocalRttMessage(text: String) {
        _rttMessages.value = _rttMessages.value + RttMessage(
            text = text,
            isRemote = false,
        )
    }

    private fun startRttReader(rttCall: Call.RttCall) {
        stopRttReader()
        currentRttCall = rttCall
        rttReaderJob = scope.launch {
            while (isActive) {
                val received = rttCall.readImmediately()
                if (received != null) {
                    processReceivedRttText(received)
                    delay(30) // Brief pause between reads when data available
                } else {
                    delay(200) // Longer idle delay to save battery
                }
            }
        }
    }

    private fun stopRttReader() {
        rttReaderJob?.cancel()
        rttReaderJob = null
        currentRttCall = null
    }

    private fun processReceivedRttText(text: String) {
        var pending = _pendingRemoteText.value
        for (char in text) {
            when (char) {
                '\n' -> {
                    if (pending.isNotBlank()) {
                        _rttMessages.value = _rttMessages.value + RttMessage(
                            text = pending,
                            isRemote = true,
                        )
                    }
                    pending = ""
                }
                '\b' -> {
                    pending = pending.dropLast(1)
                }
                else -> {
                    pending += char
                }
            }
        }
        _pendingRemoteText.value = pending
    }
}
