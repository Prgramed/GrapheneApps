package dev.egallery.domain.model

data class UploadQueueItem(
    val id: Long = 0,
    val localPath: String,
    val targetFolderId: Int,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: UploadStatus = UploadStatus.PENDING,
)

enum class UploadStatus { PENDING, UPLOADING, FAILED }
