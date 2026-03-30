package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,
    val targetFolderId: Int,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // "PENDING", "UPLOADING", "FAILED"
)
