package dev.egallery.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.MediaTagDao
import dev.egallery.data.db.entity.AlbumMediaEntity
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.domain.model.LocalExpiry
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.MediaType
import dev.egallery.domain.model.StorageStatus
import dev.egallery.util.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaTagDao: MediaTagDao,
    private val albumMediaDao: AlbumMediaDao,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val storageManager: StorageManager,
) : MediaRepository {

    override fun observeTimeline(fromDate: Long?, toDate: Long?): Flow<PagingData<MediaItem>> =
        Pager(
            config = PagingConfig(pageSize = 100, prefetchDistance = 100),
            pagingSourceFactory = { mediaDao.timelinePagingSource(fromDate, toDate) },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeFolder(folderId: Int): Flow<List<MediaItem>> =
        mediaDao.getByFolder(folderId).map { entities -> entities.map { it.toDomain() } }

    override fun observeAlbum(albumId: String): Flow<List<MediaItem>> =
        mediaDao.getByAlbum(albumId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getItemDetail(nasId: String): MediaItem? =
        mediaDao.getByNasId(nasId)?.toDomain()

    override suspend fun deleteFromNas(nasId: String): Result<Unit> {
        return try {
            immichApi.deleteAssets(kotlinx.serialization.json.buildJsonObject {
                put("ids", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(nasId))))
                put("force", kotlinx.serialization.json.JsonPrimitive(false))
            })

            // Move to trash instead of permanent delete
            mediaDao.trash(nasId, System.currentTimeMillis())

            Timber.d("Trashed (server deleted): nasId=$nasId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "deleteFromNas failed for nasId=$nasId")
            Result.failure(e)
        }
    }

    override suspend fun setTags(nasId: String, tagIds: List<String>): Result<Unit> {
        // Immich tags work differently — no-op for now
        return Result.success(Unit)
    }

    override suspend fun addToAlbum(nasId: String, albumId: String): Result<Unit> {
        return try {
            immichApi.addAssetsToAlbum(albumId, mapOf("ids" to listOf(nasId)))

            // Update local join table
            albumMediaDao.insert(AlbumMediaEntity(albumId = albumId, nasId = nasId))

            Timber.d("Added nasId=$nasId to albumId=$albumId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "addToAlbum failed for nasId=$nasId, albumId=$albumId")
            Result.failure(e)
        }
    }

    override suspend fun getCount(): Int = mediaDao.getCount()
}

fun MediaEntity.toDomain(): MediaItem = MediaItem(
    nasId = nasId,
    filename = filename,
    captureDate = captureDate,
    nasUploadDate = nasUploadDate,
    fileSize = fileSize,
    mediaType = MediaType.valueOf(mediaType),
    folderId = folderId,
    cacheKey = cacheKey,
    thumbnailPath = thumbnailPath,
    localPath = localPath,
    localExpiry = when (localExpiryType) {
        "ROLLING" -> LocalExpiry.Rolling
        "FIXED" -> localExpiryAt?.let { LocalExpiry.Fixed(it) }
        else -> null
    },
    storageStatus = StorageStatus.valueOf(storageStatus),
    lat = lat,
    lng = lng,
    nasHash = nasHash,
    lastSyncedAt = lastSyncedAt,
    isFavorite = isFavorite,
    trashedAt = trashedAt,
    isSharedSpace = isSharedSpace,
)

fun MediaItem.toEntity(): MediaEntity = MediaEntity(
    nasId = nasId,
    filename = filename,
    captureDate = captureDate,
    nasUploadDate = nasUploadDate,
    fileSize = fileSize,
    mediaType = mediaType.name,
    folderId = folderId,
    cacheKey = cacheKey,
    thumbnailPath = thumbnailPath,
    localPath = localPath,
    localExpiryType = when (localExpiry) {
        is LocalExpiry.Rolling -> "ROLLING"
        is LocalExpiry.Fixed -> "FIXED"
        null -> null
    },
    localExpiryAt = (localExpiry as? LocalExpiry.Fixed)?.expiresAt,
    storageStatus = storageStatus.name,
    lat = lat,
    lng = lng,
    nasHash = nasHash,
    lastSyncedAt = lastSyncedAt,
    isFavorite = isFavorite,
    trashedAt = trashedAt,
    isSharedSpace = isSharedSpace,
)
