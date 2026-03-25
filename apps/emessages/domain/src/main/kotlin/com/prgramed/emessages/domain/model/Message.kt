package com.prgramed.emessages.domain.model

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String = "",
    val body: String = "",
    val timestamp: Long = 0,
    val type: MessageType = MessageType.RECEIVED,
    val isRead: Boolean = true,
    val isMms: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val status: MessageStatus = MessageStatus.NONE,
    val subscriptionId: Int = -1,
)

enum class MessageType { SENT, RECEIVED, DRAFT }

enum class MessageStatus { NONE, PENDING, SENT, DELIVERED, FAILED }
