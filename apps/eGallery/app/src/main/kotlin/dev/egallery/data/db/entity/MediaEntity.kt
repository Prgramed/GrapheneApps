package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [
        Index("captureDate"),
        Index("folderId"),
        Index("storageStatus"),
        Index("lat", "lng"),
        Index("nasHash"),
    ],
)
data class MediaEntity(
    @PrimaryKey val nasId: String,
    val filename: String,
    val captureDate: Long,
    val nasUploadDate: Long = 0,
    val fileSize: Long,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val folderId: Int,
    val cacheKey: String,
    val thumbnailPath: String? = null,
    val localPath: String? = null,
    val localExpiryType: String? = null, // "ROLLING" or "FIXED"
    val localExpiryAt: Long? = null, // epoch millis (only for FIXED)
    val storageStatus: String, // "ON_DEVICE", "NAS_ONLY", "UPLOAD_PENDING", "UPLOAD_FAILED"
    val lat: Double? = null,
    val lng: Double? = null,
    val nasHash: String? = null,
    val lastSyncedAt: Long = 0,
    val isFavorite: Boolean = false,
    val trashedAt: Long? = null,
    val isSharedSpace: Boolean = false,
)
