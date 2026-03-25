package com.prgramed.emessages.domain.repository

import com.prgramed.emessages.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

data class DeletedConversation(
    val conversation: Conversation,
    val deletedAt: Long,
    val daysRemaining: Int,
)

interface ConversationRepository {
    fun getAll(): Flow<List<Conversation>>
    fun search(query: String): Flow<List<Conversation>>
    suspend fun delete(threadIds: List<Long>)
    suspend fun markAsRead(threadId: Long)
    suspend fun markAsUnread(threadId: Long)
    fun getDeletedConversations(): Flow<List<DeletedConversation>>
    suspend fun restoreConversation(threadId: Long)
    suspend fun permanentlyDelete(threadIds: List<Long>)
}
