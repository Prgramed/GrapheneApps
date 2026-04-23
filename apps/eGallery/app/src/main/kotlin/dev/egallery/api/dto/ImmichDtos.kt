package dev.egallery.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImmichAsset(
    val id: String,
    val type: String = "IMAGE", // IMAGE, VIDEO, AUDIO, OTHER
    val originalFileName: String = "",
    val fileCreatedAt: String = "", // ISO 8601
    val fileModifiedAt: String = "",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val livePhotoVideoId: String? = null,
    val thumbhash: String? = null,
    val checksum: String? = null,
    val exifInfo: ImmichExifInfo? = null,
    val ownerId: String? = null,
)

@Serializable
data class ImmichExifInfo(
    val make: String? = null,
    val model: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val fileSizeInByte: Long? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    val dateTimeOriginal: String? = null,
    val iso: String? = null,
    val fNumber: Double? = null,
    val exposureTime: String? = null,
    val focalLength: Double? = null,
    val lensModel: String? = null,
)

@Serializable
data class ImmichAlbum(
    val id: String,
    val albumName: String = "",
    val assetCount: Int = 0,
    val albumThumbnailAssetId: String? = null,
    val assets: List<ImmichAsset>? = null,
)

@Serializable
data class ImmichPerson(
    val id: String,
    val name: String = "",
    val thumbnailPath: String = "",
)

@Serializable
data class ImmichPeopleResponse(
    val people: List<ImmichPerson> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class ImmichTimeBucket(
    val timeBucket: String, // "2024-06-01"
    val count: Int = 0,
)

/** Struct-of-Arrays response from /api/timeline/bucket */
@Serializable
data class ImmichTimeBucketData(
    val id: List<String> = emptyList(),
    val city: List<String?> = emptyList(),
    val country: List<String?> = emptyList(),
    val duration: List<String?> = emptyList(),
    val visibility: List<String?> = emptyList(),
    val isFavorite: List<Boolean> = emptyList(),
    val isImage: List<Boolean> = emptyList(),
    val isTrashed: List<Boolean> = emptyList(),
    val livePhotoVideoId: List<String?> = emptyList(),
    val fileCreatedAt: List<String> = emptyList(),
    val ownerId: List<String> = emptyList(),
    val ratio: List<Double?> = emptyList(),
    val thumbhash: List<String?> = emptyList(),
)

@Serializable
data class ImmichServerInfo(
    val version: String = "",
    val versionUrl: String = "",
)

@Serializable
data class DeltaSyncRequest(
    val updatedAfter: String, // ISO 8601
    val userIds: List<String> = emptyList(),
)

@Serializable
data class ImmichMapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)

@Serializable
data class ImmichSearchResponse(
    val assets: ImmichSearchAssets = ImmichSearchAssets(),
)

@Serializable
data class ImmichSearchAssets(
    val items: List<ImmichAsset> = emptyList(),
    val total: Int = 0,
    val count: Int = 0,
    val nextPage: String? = null,
)

@Serializable
data class BulkUploadCheckResponse(
    val results: List<Map<String, String>> = emptyList(),
)

@Serializable
data class DeltaSyncResponse(
    val needsFullSync: Boolean = false,
    val upserted: List<ImmichAsset> = emptyList(),
    val deleted: List<String> = emptyList(),
)

@Serializable
data class ImmichMemory(
    val id: String = "",
    val type: String = "", // "on_this_day"
    val data: ImmichMemoryData = ImmichMemoryData(),
    val assets: List<ImmichAsset> = emptyList(),
    val createdAt: String = "",
    val memoryAt: String = "", // ISO 8601 — date the memory pertains to
    val showAt: String? = null, // ISO 8601 — start of visibility window
    val hideAt: String? = null, // ISO 8601 — end of visibility window
    val isSaved: Boolean = false,
    val deletedAt: String? = null,
)

@Serializable
data class ImmichMemoryData(
    val year: Int = 0,
)
