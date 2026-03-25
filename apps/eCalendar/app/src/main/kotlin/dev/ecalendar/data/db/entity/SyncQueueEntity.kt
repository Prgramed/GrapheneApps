package dev.ecalendar.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("accountId"),
        Index("createdAt"),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val calendarUrl: String,
    val eventUid: String,
    val operation: String, // CREATE, UPDATE, DELETE
    val icsPayload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)
