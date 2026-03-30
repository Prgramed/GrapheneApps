package dev.egallery.sync

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.api.ImmichPhotoMapper.toDomainList
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.AlbumDao
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.MediaTagDao
import dev.egallery.data.db.dao.PersonDao
import dev.egallery.data.db.dao.TagDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.db.entity.AlbumEntity
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.data.db.entity.PersonEntity
import dev.egallery.data.db.entity.UploadQueueEntity
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.data.repository.toEntity
import dev.egallery.util.HashUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class LocalPhoto(
    val path: String,
    val filename: String,
    val sha1Base64: String,
    val captureDate: Long,
    val size: Long,
    val isVideo: Boolean,
)

@Singleton
class NasSyncEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val mediaDao: MediaDao,
    private val albumDao: AlbumDao,
    private val personDao: PersonDao,
    private val tagDao: TagDao,
    private val mediaTagDao: MediaTagDao,
    private val albumMediaDao: AlbumMediaDao,
    private val uploadQueueDao: UploadQueueDao,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    private val _progress = MutableStateFlow("Idle")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun startFullSync() {
        if (syncJob?.isActive == true) return
        syncJob = syncScope.launch {
            _isRunning.value = true
            try {
                doFullSync()
            } catch (e: CancellationException) {
                _progress.value = "Cancelled"
            } catch (e: Exception) {
                _progress.value = "Failed: ${e.message?.take(80)}"
                Timber.e(e, "Full sync failed")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun startQuickSync() {
        if (syncJob?.isActive == true) return
        syncJob = syncScope.launch {
            _isRunning.value = true
            try {
                doQuickSync()
            } catch (e: CancellationException) {
                _progress.value = "Cancelled"
            } catch (e: Exception) {
                _progress.value = "Failed: ${e.message?.take(80)}"
                Timber.e(e, "Quick sync failed")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        _progress.value = "Cancelled"
        _isRunning.value = false
    }

    // ── Force Full Sync: hash-based phone↔server diff ───────────

    private suspend fun doFullSync() {
        // Step 1: Scan all device photos
        _progress.value = "Scanning device photos..."
        val devicePhotos = scanDevicePhotos()
        Timber.d("Device scan: ${devicePhotos.size} photos")

        // Step 2: Fetch all server assets
        _progress.value = "Fetching server assets..."
        val serverAssets = fetchAllServerAssets()
        Timber.d("Server assets: ${serverAssets.size}")

        // Step 3: Build lookup maps
        val serverByChecksum = mutableMapOf<String, MutableList<MediaEntity>>()
        for (entity in serverAssets) {
            if (entity.nasHash != null) {
                serverByChecksum.getOrPut(entity.nasHash) { mutableListOf() }.add(entity)
            }
        }

        // Step 4: Diff — find photos on phone but not on server
        _progress.value = "Comparing phone vs server..."
        var toUpload = 0
        var alreadyOnServer = 0
        var linked = 0

        for (photo in devicePhotos) {
            coroutineContext.job.ensureActive()

            val serverMatch = serverByChecksum[photo.sha1Base64]?.firstOrNull()
            if (serverMatch != null) {
                // Photo exists on server — link local file to server entity
                alreadyOnServer++
                if (serverMatch.localPath == null) {
                    mediaDao.updateStorageStatus(serverMatch.nasId, "ON_DEVICE", photo.path)
                    linked++
                }
            } else {
                // Photo NOT on server — check if already queued
                val existingQueue = mediaDao.getByLocalPath(photo.path)
                if (existingQueue == null) {
                    // Skip Video Boost COVER files
                    if (photo.filename.contains(".VB-01.COVER.")) continue

                    // Create temp entity + queue for upload
                    val tempId = java.util.UUID.randomUUID().toString()
                    val entity = MediaEntity(
                        nasId = tempId,
                        filename = photo.filename,
                        captureDate = photo.captureDate,
                        fileSize = photo.size,
                        mediaType = if (photo.isVideo) "VIDEO" else "PHOTO",
                        folderId = 0,
                        cacheKey = "",
                        localPath = photo.path,
                        storageStatus = "UPLOAD_PENDING",
                        nasHash = photo.sha1Base64,
                    )
                    mediaDao.upsert(entity)
                    uploadQueueDao.insert(UploadQueueEntity(localPath = photo.path, targetFolderId = 0))
                    toUpload++
                }
            }
        }

        // Step 5: Remove items deleted from server
        _progress.value = "Checking for server deletions..."
        val serverIds = serverAssets.map { it.nasId }.toSet()
        val allLocalIds = mediaDao.getAllNasIds()
        val orphaned = allLocalIds.filter { id ->
            id.isNotBlank() && id !in serverIds && !id.startsWith("-")
        }
        // Only delete NAS_ONLY items (don't touch local/pending/failed items)
        var removed = 0
        if (orphaned.size <= allLocalIds.size / 10 || orphaned.size <= 100) {
            for (id in orphaned) {
                val entity = mediaDao.getByNasId(id) ?: continue
                if (entity.storageStatus == "NAS_ONLY") {
                    mediaDao.deleteByNasId(id)
                    removed++
                }
            }
        } else {
            Timber.w("Skipping server deletion check: ${orphaned.size} orphans (>10%), likely incomplete fetch")
        }

        _progress.value = "Syncing albums..."
        syncAlbums()
        _progress.value = "Syncing people..."
        syncPeople()

        preferencesRepository.setLastSyncAt(System.currentTimeMillis())
        _progress.value = "Done: $alreadyOnServer matched, $toUpload to upload, $linked linked, $removed removed"
        Timber.d("Full sync: ${devicePhotos.size} device, ${serverAssets.size} server, $toUpload to upload, $linked linked, $removed removed")
    }

    // ── Quick Sync: date-based ──────────────────────────────────

    private suspend fun doQuickSync() {
        _progress.value = "Quick sync..."
        val lastSyncAt = preferencesRepository.getLastSyncAtOnce()

        // 1. Get new server assets since last sync
        val buckets = immichApi.getTimeBuckets("MONTH")
        var newFromServer = 0

        for (bucket in buckets) {
            coroutineContext.job.ensureActive()
            val bucketData = immichApi.getTimeBucket(bucket.timeBucket, "MONTH")
            val items = bucketData.toDomainList()
            val newItems = items.filter { it.captureDate > lastSyncAt }
            if (newItems.isEmpty() && items.isNotEmpty()) break

            if (newItems.isNotEmpty()) {
                val entities = newItems.map { item ->
                    val existing = mediaDao.getByNasId(item.nasId)
                    if (existing != null && existing.storageStatus != "NAS_ONLY") {
                        item.copy(
                            storageStatus = dev.egallery.domain.model.StorageStatus.valueOf(existing.storageStatus),
                            localPath = existing.localPath,
                        ).toEntity()
                    } else {
                        item.toEntity()
                    }
                }
                mediaDao.upsertAll(entities)
                newFromServer += entities.size
            }
        }

        // 2. Check for new device photos since last sync
        _progress.value = "Checking new device photos..."
        val newDevicePhotos = scanDevicePhotosSince(lastSyncAt)
        var toUpload = 0

        if (newDevicePhotos.isNotEmpty()) {
            // Bulk check against Immich
            val checksums = newDevicePhotos.map {
                mapOf("id" to it.filename, "checksum" to it.sha1Base64)
            }
            // Check in batches of 100
            for (batch in checksums.chunked(100)) {
                coroutineContext.job.ensureActive()
                try {
                    val result = immichApi.bulkUploadCheck(mapOf("assets" to batch))
                    val rejectIds = result.results
                        .filter { it["action"] == "reject" }
                        .mapNotNull { it["id"] }
                        .toSet()

                    for (photo in newDevicePhotos) {
                        if (photo.filename in rejectIds) continue // Already on server
                        if (photo.filename.contains(".VB-01.COVER.")) continue
                        if (mediaDao.getByLocalPath(photo.path) != null) continue

                        val tempId = java.util.UUID.randomUUID().toString()
                        val entity = MediaEntity(
                            nasId = tempId,
                            filename = photo.filename,
                            captureDate = photo.captureDate,
                            fileSize = photo.size,
                            mediaType = if (photo.isVideo) "VIDEO" else "PHOTO",
                            folderId = 0,
                            cacheKey = "",
                            localPath = photo.path,
                            storageStatus = "UPLOAD_PENDING",
                            nasHash = photo.sha1Base64,
                        )
                        mediaDao.upsert(entity)
                        uploadQueueDao.insert(UploadQueueEntity(localPath = photo.path, targetFolderId = 0))
                        toUpload++
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Bulk upload check failed, skipping batch")
                }
            }
        }

        preferencesRepository.setLastSyncAt(System.currentTimeMillis())
        _progress.value = if (newFromServer > 0 || toUpload > 0) {
            "Done: $newFromServer from server, $toUpload to upload"
        } else {
            "Done: up to date"
        }
    }

    // ── Device Photo Scanner ────────────────────────────────────

    private fun scanDevicePhotos(): List<LocalPhoto> = scanMediaStore(null)

    private fun scanDevicePhotosSince(sinceMs: Long): List<LocalPhoto> = scanMediaStore(sinceMs)

    private fun scanMediaStore(sinceMs: Long?): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()

        for (uri in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            val isVideo = uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            val selection = if (sinceMs != null) "${MediaStore.MediaColumns.DATE_MODIFIED} > ?" else null
            val selectionArgs = if (sinceMs != null) arrayOf((sinceMs / 1000).toString()) else null

            appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val filename = cursor.getString(nameCol) ?: continue
                    val localPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (localPath.isNullOrBlank()) continue

                    val file = File(localPath)
                    if (!file.exists()) continue

                    val size = cursor.getLong(sizeCol)
                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateModified = cursor.getLong(dateModCol) * 1000L
                    val captureDate = if (dateTaken > 0) dateTaken else dateModified

                    // Compute SHA1 (base64) to match Immich's checksum format
                    val sha1 = try {
                        computeSha1Base64(file)
                    } catch (e: Exception) {
                        Timber.w("Failed to hash $filename: ${e.message}")
                        continue
                    }

                    photos.add(LocalPhoto(
                        path = localPath,
                        filename = filename,
                        sha1Base64 = sha1,
                        captureDate = captureDate,
                        size = size,
                        isVideo = isVideo,
                    ))
                }
            }
        }

        return photos
    }

    private fun computeSha1Base64(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP)
    }

    // ── Fetch all server assets ─────────────────────────────────

    private suspend fun fetchAllServerAssets(): List<MediaEntity> {
        val buckets = immichApi.getTimeBuckets("MONTH")
        val totalAssets = buckets.sumOf { it.count }
        val allEntities = mutableListOf<MediaEntity>()
        var synced = 0

        for (bucket in buckets) {
            coroutineContext.job.ensureActive()
            _progress.value = "Fetching server: $synced / $totalAssets..."

            val bucketData = immichApi.getTimeBucket(bucket.timeBucket, "MONTH")
            val items = bucketData.toDomainList()

            // For each item, get checksum by fetching asset detail
            // This is expensive — only do for full sync
            for (item in items) {
                coroutineContext.job.ensureActive()
                val existing = mediaDao.getByNasId(item.nasId)
                val entity = if (existing != null && existing.storageStatus != "NAS_ONLY") {
                    item.copy(
                        storageStatus = dev.egallery.domain.model.StorageStatus.valueOf(existing.storageStatus),
                        localPath = existing.localPath,
                        nasHash = existing.nasHash,
                    ).toEntity()
                } else {
                    item.toEntity()
                }
                allEntities.add(entity)
            }

            mediaDao.upsertAll(allEntities.takeLast(items.size))
            synced += items.size
        }

        // Fetch checksums for items that don't have them (needed for diff)
        _progress.value = "Fetching checksums..."
        val needChecksum = allEntities.filter { it.nasHash.isNullOrBlank() }
        var fetched = 0
        for (entity in needChecksum) {
            coroutineContext.job.ensureActive()
            try {
                val asset = immichApi.getAsset(entity.nasId)
                if (asset.checksum != null) {
                    mediaDao.updateHash(entity.nasId, asset.checksum)
                    // Update in our list too
                    val idx = allEntities.indexOfFirst { it.nasId == entity.nasId }
                    if (idx >= 0) {
                        allEntities[idx] = entity.copy(nasHash = asset.checksum)
                    }
                }
                fetched++
                if (fetched % 100 == 0) {
                    _progress.value = "Fetching checksums: $fetched / ${needChecksum.size}..."
                }
            } catch (e: Exception) {
                Timber.w("Failed to get checksum for ${entity.nasId}")
            }
        }

        return allEntities
    }

    // ── Albums, People ──────────────────────────────────────────

    private suspend fun syncAlbums() {
        try {
            val albums = immichApi.getAlbums()
            val entities = albums.map { album ->
                AlbumEntity(
                    id = album.id,
                    name = album.albumName,
                    coverPhotoId = album.albumThumbnailAssetId,
                    photoCount = album.assetCount,
                    type = "MANUAL",
                )
            }
            albumDao.upsertAll(entities)
        } catch (e: Exception) {
            Timber.w(e, "Album sync failed")
        }
    }

    private suspend fun syncPeople() {
        try {
            val response = immichApi.getPeople()
            val entities = response.people.map { person ->
                PersonEntity(id = person.id, name = person.name, coverPhotoId = null, photoCount = 0)
            }
            personDao.upsertAll(entities)
        } catch (e: Exception) {
            Timber.w(e, "People sync failed")
        }
    }

    suspend fun syncAlbumItems(albumId: String) {
        try {
            val album = immichApi.getAlbum(albumId)
            albumMediaDao.deleteByAlbum(albumId)
            album.assets?.let { assets ->
                val items = assets.mapNotNull { dev.egallery.api.ImmichPhotoMapper.run { it.toDomain() } }
                val entities = items.map { it.toEntity() }
                mediaDao.upsertAll(entities)
                val joins = items.map { dev.egallery.data.db.entity.AlbumMediaEntity(albumId = albumId, nasId = it.nasId) }
                albumMediaDao.insertAll(joins)
            }
        } catch (e: Exception) {
            Timber.w(e, "Album items sync failed")
        }
    }
}
