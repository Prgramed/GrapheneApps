package com.prgramed.emessages.feature.chat

import android.telephony.SmsMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.emessages.domain.model.Recipient
import com.prgramed.emessages.domain.model.SimInfo
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import com.prgramed.emessages.domain.repository.MessageRepository
import com.prgramed.emessages.domain.repository.SimRepository
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

data class NewMessageUiState(
    val recipientAddress: String = "",
    val selectedContactName: String? = null,
    val messageText: String = "",
    val isSending: Boolean = false,
    val contactResults: List<Recipient> = emptyList(),
    val availableSims: List<SimInfo> = emptyList(),
    val activeSim: SimInfo? = null,
    val attachmentUri: String? = null,
    val segmentInfo: SegmentInfo? = null,
)

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val contactLookup: ContactLookupRepository,
    private val simRepository: SimRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewMessageUiState())
    val uiState: StateFlow<NewMessageUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        val address = savedStateHandle.get<String>("address") ?: ""
        val body = savedStateHandle.get<String>("body") ?: ""
        if (address.isNotBlank()) {
            _uiState.update { it.copy(recipientAddress = address) }
        }
        if (body.isNotBlank()) {
            _uiState.update { it.copy(messageText = body) }
        }
        val attachment = savedStateHandle.get<String>("attachment") ?: ""
        if (attachment.isNotBlank()) {
            _uiState.update { it.copy(attachmentUri = java.net.URLDecoder.decode(attachment, "UTF-8")) }
        }
        loadSims()
    }

    fun onSimSelected(sim: SimInfo) {
        _uiState.update { it.copy(activeSim = sim) }
    }

    private fun loadSims() {
        viewModelScope.launch {
            simRepository.getActiveSimsFlow().collect { sims ->
                val defaultSubId = simRepository.getDefaultSmsSubscriptionId()
                val defaultSim = sims.find { it.subscriptionId == defaultSubId } ?: sims.firstOrNull()
                _uiState.update {
                    it.copy(
                        availableSims = sims,
                        activeSim = it.activeSim ?: defaultSim,
                    )
                }
            }
        }
    }

    fun onRecipientChanged(address: String) {
        _uiState.update { it.copy(recipientAddress = address, selectedContactName = null) }
        searchContacts(address)
    }

    fun selectContact(recipient: Recipient) {
        _uiState.update {
            it.copy(
                recipientAddress = recipient.address,
                selectedContactName = recipient.contactName,
                contactResults = emptyList(),
            )
        }
    }

    fun onMessageTextChanged(text: String) {
        _uiState.update { it.copy(messageText = text, segmentInfo = computeSegmentInfo(text)) }
    }

    fun onAttachmentSelected(uri: String) {
        _uiState.update { it.copy(attachmentUri = uri) }
    }

    fun onClearAttachment() {
        _uiState.update { it.copy(attachmentUri = null) }
    }

    fun sendMessage(onNavigateToChat: (Long) -> Unit) {
        val address = _uiState.value.recipientAddress.trim()
        val text = _uiState.value.messageText.trim()
        val attachment = _uiState.value.attachmentUri

        if (address.isBlank() || (text.isBlank() && attachment == null)) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThreadId(listOf(address))
                if (threadId != 0L) {
                    if (attachment != null) {
                        messageRepository.sendMms(
                            listOf(address),
                            text,
                            listOf(android.net.Uri.parse(attachment)),
                        )
                    } else {
                        messageRepository.sendSms(
                            address, text, _uiState.value.activeSim?.subscriptionId,
                        )
                    }
                    onNavigateToChat(threadId)
                }
            } catch (_: Exception) {
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun searchContacts(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(contactResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(150) // debounce
            val results = withContext(Dispatchers.IO) {
                contactLookup.searchContacts(query)
            }
            _uiState.update { it.copy(contactResults = results.take(50)) }
        }
    }

    private fun computeSegmentInfo(text: String): SegmentInfo? {
        if (text.isEmpty()) return null
        val result = SmsMessage.calculateLength(text, false)
        val segments = result[0]
        val remaining = result[2]
        val limit = if (result[3] == SmsMessage.ENCODING_7BIT) 160 else 70
        if (text.length <= limit / 2) return null
        return SegmentInfo(segments, remaining, limit)
    }
}
