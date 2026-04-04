package com.prgramed.emessages.feature.chat

import android.telephony.SmsMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.emessages.data.linkpreview.LinkPreviewFetcher
import com.prgramed.emessages.domain.model.LinkPreview
import com.prgramed.emessages.domain.model.Message
import com.prgramed.emessages.domain.model.SimInfo
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import com.prgramed.emessages.domain.repository.ConversationRepository
import com.prgramed.emessages.domain.repository.MessageRepository
import com.prgramed.emessages.domain.repository.SimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SegmentInfo(val segments: Int, val remaining: Int, val limit: Int)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val recipientName: String = "",
    val recipientAddress: String = "",
    val recipientPhotoUri: String? = null,
    val messageText: String = "",
    val attachmentUri: String? = null,
    val isLoading: Boolean = true,
    val availableSims: List<SimInfo> = emptyList(),
    val activeSim: SimInfo? = null,
    val isDualSim: Boolean = false,
    val selectedMessage: Message? = null,
    val segmentInfo: SegmentInfo? = null,
    val hasMoreMessages: Boolean = true,
    val scrollToBottomEvent: Int = 0,
    val sendError: String? = null,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val replyToMessage: Message? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val contactLookupRepository: ContactLookupRepository,
    private val simRepository: SimRepository,
    private val linkPreviewFetcher: LinkPreviewFetcher,
) : ViewModel() {

    private val threadId: Long = savedStateHandle["threadId"] ?: 0L

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val olderMessages = mutableListOf<Message>()

    private val _linkPreviews = MutableStateFlow<Map<Long, LinkPreview>>(emptyMap())
    val linkPreviews: StateFlow<Map<Long, LinkPreview>> = _linkPreviews.asStateFlow()

    private val urlPattern = java.util.regex.Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)",
        java.util.regex.Pattern.CASE_INSENSITIVE,
    )

    init {
        // Restore draft from SavedStateHandle (survives process death) or DraftStore
        val savedDraft = savedStateHandle.get<String>("draft_$threadId")
            ?: com.prgramed.emessages.domain.model.DraftStore.get(threadId)
        if (!savedDraft.isNullOrBlank()) {
            _uiState.update { it.copy(messageText = savedDraft) }
            com.prgramed.emessages.domain.model.DraftStore.remove(threadId)
        }
        loadMessages()
        markAsRead()
        loadSims()
    }

    fun onMessageTextChanged(text: String) {
        _uiState.update { it.copy(messageText = text, segmentInfo = computeSegmentInfo(text)) }
        // Persist draft to SavedStateHandle for process death survival
        savedStateHandle["draft_$threadId"] = text
    }

    fun onAttachmentSelected(uri: String) {
        _uiState.update { it.copy(attachmentUri = uri) }
    }

    fun onClearAttachment() {
        _uiState.update { it.copy(attachmentUri = null) }
    }

    fun onMessageSelected(message: Message?) {
        _uiState.update { it.copy(selectedMessage = message) }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            messageRepository.deleteMessage(message.id, message.isMms)
            olderMessages.removeAll { it.id == message.id && it.isMms == message.isMms }
            _uiState.update { it.copy(selectedMessage = null) }
        }
    }

    fun retryMmsDownload(mmsId: Long, contentLocation: String) {
        viewModelScope.launch {
            messageRepository.retryMmsDownload(mmsId, contentLocation)
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val oldest = _uiState.value.messages.minByOrNull { it.timestamp }
            val beforeTs = oldest?.timestamp ?: return@launch
            val older = messageRepository.loadOlderMessages(threadId, beforeTs)
            olderMessages.addAll(older)
            fetchLinkPreviews(older)
            _uiState.update {
                it.copy(
                    messages = mergeMessages(it.messages, olderMessages),
                    hasMoreMessages = older.size >= PAGE_SIZE,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun sendMessage() {
        val replyTo = _uiState.value.replyToMessage
        val rawText = _uiState.value.messageText.trim()
        val text = if (replyTo != null && rawText.isNotBlank()) {
            val quotedLine = replyTo.body.lines().first().take(80)
            "> $quotedLine\n\n$rawText"
        } else rawText
        val address = _uiState.value.recipientAddress
        val attachment = _uiState.value.attachmentUri

        if (text.isBlank() && attachment == null) return
        if (address.isBlank()) return
        if (_uiState.value.isSending) return // Guard against double-send

        _uiState.update {
            it.copy(
                messageText = "",
                attachmentUri = null,
                segmentInfo = null,
                isSending = true,
                replyToMessage = null,
                scrollToBottomEvent = it.scrollToBottomEvent + 1,
            )
        }
        viewModelScope.launch {
            try {
                if (attachment != null) {
                    messageRepository.sendMms(
                        listOf(address),
                        text,
                        listOf(android.net.Uri.parse(attachment)),
                    )
                } else {
                    messageRepository.sendSms(address, text, _uiState.value.activeSim?.subscriptionId)
                }
            } catch (e: Exception) {
                // Restore message text so user doesn't lose it
                _uiState.update { it.copy(messageText = text, attachmentUri = attachment, sendError = "Failed to send: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }

    fun onReplyToMessage(message: Message) {
        _uiState.update { it.copy(replyToMessage = message) }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyToMessage = null) }
    }

    fun onSimSelected(sim: SimInfo) {
        _uiState.update { it.copy(activeSim = sim) }
        viewModelScope.launch {
            simRepository.setPreferredSim(threadId, sim.subscriptionId)
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            messageRepository.getMessages(threadId)
                .catch { /* Silently fail */ }
                .collect { latestMessages ->
                    // Mark any newly arrived unread messages as read
                    if (latestMessages.any { !it.isRead }) {
                        markAsRead()
                    }
                    val allMessages = mergeMessages(latestMessages, olderMessages)
                    val address = allMessages.firstOrNull { it.address.isNotBlank() }?.address ?: ""
                    val (recipientName, photoUri) = if (address.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            val recipient = contactLookupRepository.lookupContact(address)
                            Pair(
                                recipient.contactName ?: recipient.address,
                                recipient.contactPhotoUri,
                            )
                        }
                    } else {
                        Pair("", null)
                    }
                    _uiState.update {
                        it.copy(
                            messages = allMessages,
                            recipientName = recipientName,
                            recipientAddress = address,
                            recipientPhotoUri = photoUri,
                            isLoading = false,
                        )
                    }
                    fetchLinkPreviews(allMessages)
                }
        }
    }

    private fun mergeMessages(latest: List<Message>, older: List<Message>): List<Message> {
        if (older.isEmpty()) return latest
        val oldestLatest = latest.minByOrNull { it.timestamp }?.timestamp ?: return latest
        val filteredOlder = older.filter { it.timestamp < oldestLatest }
        return (filteredOlder + latest).sortedBy { it.timestamp }
    }

    private fun loadSims() {
        viewModelScope.launch {
            simRepository.getActiveSimsFlow().collect { sims ->
                val isDual = sims.size >= 2
                _uiState.update { it.copy(availableSims = sims, isDualSim = isDual) }

                if (isDual && _uiState.value.activeSim == null) {
                    val preferredSubId = simRepository.getPreferredSim(threadId).first()
                    val sim = if (preferredSubId != null) {
                        sims.find { it.subscriptionId == preferredSubId }
                    } else null
                    val activeSim = sim ?: sims.find {
                        it.subscriptionId == simRepository.getDefaultSmsSubscriptionId()
                    } ?: sims.firstOrNull()
                    _uiState.update { it.copy(activeSim = activeSim) }
                }
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            conversationRepository.markAsRead(threadId)
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

    private val linkPreviewSemaphore = kotlinx.coroutines.sync.Semaphore(6)

    private fun fetchLinkPreviews(messages: List<Message>) {
        val current = _linkPreviews.value
        messages.takeLast(30).forEach { message ->
            if (message.id in current) return@forEach
            val matcher = urlPattern.matcher(message.body)
            if (matcher.find()) {
                val url = matcher.group() ?: return@forEach
                viewModelScope.launch {
                    linkPreviewSemaphore.withPermit {
                        val preview = linkPreviewFetcher.fetch(url)
                        if (preview != null) {
                            _linkPreviews.update { it + (message.id to preview) }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        // Save draft when leaving conversation
        val text = _uiState.value.messageText
        com.prgramed.emessages.domain.model.DraftStore.save(threadId, text)
        super.onCleared()
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
