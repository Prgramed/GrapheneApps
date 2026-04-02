package dev.egallery.api

import dev.egallery.api.dto.ImmichAsset
import dev.egallery.api.dto.ImmichTimeBucketData
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.MediaType
import dev.egallery.domain.model.StorageStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ImmichPhotoMapper {

    /** Map struct-of-arrays bucket data to domain items */
    fun ImmichTimeBucketData.toDomainList(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        for (i in id.indices) {
            val assetId = id[i]
            if (assetId.isBlank()) continue
            if (isTrashed.getOrElse(i) { false }) continue

            val isImage = isImage.getOrElse(i) { true }
            val liveVideoId = livePhotoVideoId.getOrElse(i) { null }
            val favorite = isFavorite.getOrElse(i) { false }
            val dateStr = fileCreatedAt.getOrElse(i) { "" }

            val mediaType = when {
                liveVideoId != null -> MediaType.LIVE_PHOTO
                !isImage -> MediaType.VIDEO
                else -> MediaType.PHOTO
            }

            items.add(
                MediaItem(
                    nasId = assetId,
                    filename = assetId, // Bucket data doesn't include filename
                    captureDate = parseDateTime(dateStr),
                    fileSize = 0L,
                    mediaType = mediaType,
                    folderId = 0,
                    cacheKey = assetId,
                    storageStatus = StorageStatus.NAS_ONLY,
                    lastSyncedAt = System.currentTimeMillis(),
                    isFavorite = favorite,
                    isSharedSpace = false,
                ),
            )
        }
        return items
    }

    /** Map single asset (from /api/assets/{id}) to domain */
    fun ImmichAsset.toDomain(): MediaItem? {
        if (id.isBlank()) return null
        if (isTrashed || isArchived) return null

        val mediaType = when {
            livePhotoVideoId != null -> MediaType.LIVE_PHOTO
            type.equals("VIDEO", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.PHOTO
        }

        return MediaItem(
            nasId = id,
            filename = originalFileName.ifBlank { id },
            captureDate = parseIso8601(fileCreatedAt),
            nasUploadDate = parseIso8601(fileModifiedAt),
            fileSize = exifInfo?.fileSizeInByte ?: 0L,
            mediaType = mediaType,
            folderId = 0,
            cacheKey = id,
            storageStatus = StorageStatus.NAS_ONLY,
            lat = exifInfo?.latitude,
            lng = exifInfo?.longitude,
            nasHash = checksum,
            lastSyncedAt = System.currentTimeMillis(),
            isFavorite = isFavorite,
            isSharedSpace = false,
        )
    }

    private fun parseDateTime(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            LocalDateTime.parse(dateStr).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
            parseIso8601(dateStr)
        }
    }

    private fun parseIso8601(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(dateStr)).toEpochMilli()
        } catch (_: Exception) {
            try { Instant.parse(dateStr).toEpochMilli() } catch (_: Exception) {
                timber.log.Timber.w("Failed to parse date: $dateStr")
                0L
            }
        }
    }
}
