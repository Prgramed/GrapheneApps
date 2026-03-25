package com.prgramed.econtacts.domain.model

data class RecentCall(
    val contactId: Long? = null,
    val name: String? = null,
    val number: String,
    val type: CallType,
    val timestamp: Long,
    val duration: Long,
)

enum class CallType {
    INCOMING, OUTGOING, MISSED,
}
