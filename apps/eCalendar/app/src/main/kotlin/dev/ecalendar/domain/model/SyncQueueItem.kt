package dev.ecalendar.domain.model

enum class SyncOp {
    CREATE,
    UPDATE,
    DELETE,
}

data class SyncQueueItem(
    val id: Long = 0,
    val accountId: Long,
    val calendarUrl: String,
    val eventUid: String,
    val operation: SyncOp,
    val icsPayload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)
