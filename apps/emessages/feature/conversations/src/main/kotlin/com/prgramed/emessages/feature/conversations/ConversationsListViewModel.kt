package com.prgramed.emessages.feature.conversations

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.emessages.domain.model.Conversation
import com.prgramed.emessages.domain.model.SimInfo
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import com.prgramed.emessages.domain.repository.ConversationRepository
import com.prgramed.emessages.domain.repository.SimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val KEY_PINNED_THREADS = stringSetPreferencesKey("pinned_threads")

data class ConversationsListUiState(
    val conversations: List<Conversation> = emptyList(),
    val pinnedThreadIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDualSim: Boolean = false,
    val availableSims: List<SimInfo> = emptyList(),
)

@HiltViewModel
class ConversationsListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val simRepository: SimRepository,
    private val contactLookup: ContactLookupRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsListUiState())
    val uiState: StateFlow<ConversationsListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    // Tracks threads whose read status was toggled but may not yet be reflected
    // in the ContentProvider reload. Cleared once the provider confirms the state.
    private val forcedReadThreadIds = mutableSetOf<Long>()
    private val forcedUnreadThreadIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            simRepository.getActiveSimsFlow().collect { sims ->
                _uiState.update {
                    it.copy(availableSims = sims, isDualSim = sims.size >= 2)
                }
            }
        }
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val pinned = prefs[KEY_PINNED_THREADS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
            _uiState.update { it.copy(pinnedThreadIds = pinned) }
        }
    }

    fun togglePin(threadId: Long) {
        val current = _uiState.value.pinnedThreadIds
        val updated = if (threadId in current) current - threadId else current + threadId
        _uiState.update { it.copy(pinnedThreadIds = updated) }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_PINNED_THREADS] = updated.map { it.toString() }.toSet()
            }
        }
    }

    fun isPinned(threadId: Long): Boolean = threadId in _uiState.value.pinnedThreadIds

    private var permissionGranted = false

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            permissionGranted = true
            if (observeJob == null || observeJob?.isActive != true) {
                startObserving()
            }
        } else {
            _uiState.update { it.copy(isLoading = false, error = "SMS permission required") }
        }
    }

    fun refresh() {
        if (permissionGranted) {
            startObserving()
        }
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        observeJob?.cancel()
        if (query.isBlank()) {
            startObserving()
        } else {
            observeJob = viewModelScope.launch {
                kotlinx.coroutines.delay(250) // Debounce search
                conversationRepository.search(query)
                    .catch { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                    .collect { conversations ->
                        _uiState.update {
                            it.copy(
                                conversations = conversations,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
            }
        }
    }

    fun deleteConversation(threadId: Long) {
        // Remove from UI immediately
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations.filter { it.threadId != threadId },
            )
        }
        viewModelScope.launch {
            conversationRepository.delete(listOf(threadId))
        }
    }

    fun markThreadAsRead(threadId: Long) {
        forcedReadThreadIds.add(threadId)
        forcedUnreadThreadIds.remove(threadId)
        applyReadOverrides()
        viewModelScope.launch { conversationRepository.markAsRead(threadId) }
    }

    fun markThreadAsUnread(threadId: Long) {
        forcedUnreadThreadIds.add(threadId)
        forcedReadThreadIds.remove(threadId)
        applyReadOverrides()
        viewModelScope.launch { conversationRepository.markAsUnread(threadId) }
    }

    private fun applyReadOverrides() {
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations.map { conv ->
                    when (conv.threadId) {
                        in forcedReadThreadIds -> {
                            if (conv.unreadCount == 0) {
                                // Provider caught up, clear override
                                forcedReadThreadIds.remove(conv.threadId)
                                conv
                            } else {
                                conv.copy(unreadCount = 0)
                            }
                        }
                        in forcedUnreadThreadIds -> {
                            if (conv.unreadCount > 0) {
                                forcedUnreadThreadIds.remove(conv.threadId)
                                conv
                            } else {
                                conv.copy(unreadCount = 1)
                            }
                        }
                        else -> conv
                    }
                },
            )
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            conversationRepository.getAll()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { conversations ->
                    // Resolve contact names inline before updating UI
                    val resolved = withContext(Dispatchers.IO) {
                        conversations.map { conv ->
                            val resolvedRecipients = conv.recipients.map { r ->
                                if (r.contactName == null) contactLookup.lookupContact(r.address) else r
                            }
                            conv.copy(recipients = resolvedRecipients)
                        }
                    }
                    val pinned = _uiState.value.pinnedThreadIds
                    val sorted = if (pinned.isEmpty()) resolved else {
                        val (pinnedConvs, unpinned) = resolved.partition { it.threadId in pinned }
                        pinnedConvs + unpinned
                    }
                    _uiState.update {
                        it.copy(
                            conversations = sorted,
                            isLoading = false,
                            error = null,
                        )
                    }
                    applyReadOverrides()
                }
        }
    }

}
