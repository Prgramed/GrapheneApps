package com.prgramed.emessages.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.emessages.domain.repository.ConversationRepository
import com.prgramed.emessages.domain.repository.DeletedConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentlyDeletedUiState(
    val conversations: List<DeletedConversation> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentlyDeletedUiState())
    val uiState: StateFlow<RecentlyDeletedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            conversationRepository.getDeletedConversations()
                .catch { }
                .collect { deleted ->
                    _uiState.update {
                        it.copy(conversations = deleted, isLoading = false)
                    }
                }
        }
    }

    fun restore(threadId: Long) {
        _uiState.update { state ->
            state.copy(conversations = state.conversations.filter { it.conversation.threadId != threadId })
        }
        viewModelScope.launch {
            conversationRepository.restoreConversation(threadId)
        }
    }

    fun permanentlyDelete(threadId: Long) {
        _uiState.update { state ->
            state.copy(conversations = state.conversations.filter { it.conversation.threadId != threadId })
        }
        viewModelScope.launch {
            conversationRepository.permanentlyDelete(listOf(threadId))
        }
    }

    fun deleteAll() {
        val threadIds = _uiState.value.conversations.map { it.conversation.threadId }
        if (threadIds.isEmpty()) return
        _uiState.update { it.copy(conversations = emptyList()) }
        viewModelScope.launch {
            conversationRepository.permanentlyDelete(threadIds)
        }
    }
}
