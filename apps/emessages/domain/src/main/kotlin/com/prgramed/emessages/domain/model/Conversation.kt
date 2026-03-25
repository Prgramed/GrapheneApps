package com.prgramed.emessages.domain.model

data class Conversation(
    val threadId: Long,
    val recipients: List<Recipient> = emptyList(),
    val snippet: String = "",
    val timestamp: Long = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val lastMessageSubscriptionId: Int = -1,
    val hasDraft: Boolean = false,
)
