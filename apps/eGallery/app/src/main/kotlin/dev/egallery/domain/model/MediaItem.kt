package dev.egallery.domain.model

data class MediaItem(
    val nasId: String,
    val filename: String,
    val captureDate: Long,
    val nasUploadDate: Long = 0,
    val fileSize: Long,
    val mediaType: MediaType,
    val folderId: Int,
    val cacheKey: String,
    val thumbnailPath: String? = null,
    val localPath: String? = null,
    val localExpiry: LocalExpiry? = null,
    val storageStatus: StorageStatus,
    val lat: Double? = null,
    val lng: Double? = null,
    val nasHash: String? = null,
    val lastSyncedAt: Long = 0,
    val isFavorite: Boolean = false,
    val trashedAt: Long? = null,
    val isSharedSpace: Boolean = false,
)

enum class MediaType { PHOTO, VIDEO, LIVE_PHOTO }

enum class StorageStatus { ON_DEVICE, NAS_ONLY, UPLOAD_PENDING, UPLOAD_FAILED, TRASHED }

sealed class LocalExpiry {
    data object Rolling : LocalExpiry()
    data class Fixed(val expiresAt: Long) : LocalExpiry()
}
