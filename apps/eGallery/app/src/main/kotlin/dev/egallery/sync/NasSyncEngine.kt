package dev.egallery.sync

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.api.ImmichPhotoMapper
import dev.egallery.api.ImmichPhotoMapper.toDomainList
import dev.egallery.api.ImmichPhotoService
import dev.egallery.api.dto.DeltaSyncRequest
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    val dateModified: Long = 0L,
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

    /**
     * Merges a server asset with any existing local entry that has the same hash.
     * Prevents duplicates when the same photo exists on device (temp nasId) and server (real nasId).
     */
    private suspend fun mergeWithExisting(item: dev.egallery.domain.model.MediaItem): dev.egallery.data.db.entity.MediaEntity {
        // Check by nasId first (existing server entry)
        val byNasId = mediaDao.getByNasId(item.nasId)
        if (byNasId != null && byNasId.storageStatus != "NAS") {
            return item.copy(
                storageStatus = dev.egallery.domain.model.StorageStatus.valueOf(byNasId.storageStatus),
                localPath = byNasId.localPath,
            ).toEntity()
        }

        // Check by hash — catches device-only entries with temp nasId
        val nasHash = item.nasHash
        if (!nasHash.isNullOrBlank()) {
            val byHash = mediaDao.getByHash(nasHash)
            if (byHash != null && byHash.nasId != item.nasId && byHash.localPath != null) {
                // Found local duplicate — merge: delete temp entry, keep localPath
                mediaDao.deleteByNasId(byHash.nasId)
                return item.copy(
                    storageStatus = dev.egallery.domain.model.StorageStatus.SYNCED,
                    localPath = byHash.localPath,
                ).toEntity()
            }
        }

        return item.toEntity()
    }

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
                // Try delta sync first (fast path)
                val deltaOk = try {
                    doDeltaSync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Delta sync failed, falling back to quick sync")
                    false
                }
                if (!deltaOk) {
                    doQuickSync()
                }
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

    /** Suspending version for WorkManager — runs inline, doesn't return until done. */
    suspend fun doQuickSyncSuspend() {
        _isRunning.value = true
        try {
            val deltaOk = try {
                doDeltaSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Delta sync failed, falling back to quick sync")
                false
            }
            if (!deltaOk) {
                doQuickSync()
            }
        } finally {
            _isRunning.value = false
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        _progress.value = "Cancelled"
        _isRunning.value = false
    }

    // ── Delta Sync: instant server changes via /api/sync/delta-sync ──

    private suspend fun doDeltaSync(): Boolean {
        val lastSyncMs = preferencesRepository.getLastSyncAtOnce()
        if (lastSyncMs == 0L) return false // No baseline — need full sync

        _progress.value = "Delta sync..."

        val isoTimestamp = Instant.ofEpochMilli(lastSyncMs)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_DATE_TIME)

        val response = immichApi.deltaSync(DeltaSyncRequest(updatedAfter = isoTimestamp))

        if (response.needsFullSync) {
            Timber.d("Server requires full sync")
            return false
        }

        // Process upserts — skip items that are TRASHED locally
        var upserted = 0
        if (response.upserted.isNotEmpty()) {
            val items = response.upserted.mapNotNull { asset ->
                ImmichPhotoMapper.run { asset.toDomain() }
            }
            val entities = items.mapNotNull { item ->
                val existing = mediaDao.getByNasId(item.nasId)
                if (existing?.trashedAt != null) return@mapNotNull null
                mergeWithExisting(item)
            }
            mediaDao.upsertAll(entities)
            upserted = entities.size
        }

        // Process deletions
        var deleted = 0
        for (deletedId in response.deleted) {
            val entity = mediaDao.getByNasId(deletedId) ?: continue
            if (entity.storageStatus == "NAS") {
                mediaDao.deleteByNasId(deletedId)
                deleted++
            }
        }

        _progress.value = "Syncing albums..."
        syncAlbums()
        _progress.value = "Syncing people..."
        syncPeople()

        preferencesRepository.setLastSyncAt(System.currentTimeMillis())
        _progress.value = "Done: $upserted updated, $deleted removed"
        Timber.d("Delta sync: $upserted upserted, $deleted deleted")
        return true
    }

    // ── Force Full Sync: hash-based phone↔server diff ───────────

    private suspend fun doFullSync() {
        // Step 1: Scan all device photos (with hash caching)
        _progress.value = "Scanning device photos..."
        val devicePhotos = scanDevicePhotos()
        Timber.d("Device scan: ${devicePhotos.size} photos")

        // Step 2: Fetch all server assets (parallelized checksum fetch)
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
                // But skip if locally trashed (don't un-trash)
                if (serverMatch.trashedAt != null) continue
                alreadyOnServer++
                if (serverMatch.localPath == null) {
                    mediaDao.updateStorageStatus(serverMatch.nasId, "SYNCED", photo.path)
                    linked++
                }
            } else {
                // Photo NOT on server — check if already queued
                val existingQueue = mediaDao.getByLocalPath(photo.path)
                if (existingQueue == null) {
                    // Skip Video Boost COVER files and third-party app directories
                    if (photo.filename.contains(".VB-01.COVER.")) continue
                    if (!isUploadablePath(photo.path)) continue

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
                        storageStatus = "DEVICE",
                        nasHash = photo.sha1Base64,
                        localFileModifiedAt = photo.dateModified,
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
                if (entity.storageStatus == "NAS") {
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

    // ── Quick Sync: date-based (fallback when delta fails) ─────

    private suspend fun doQuickSync() {
        _progress.value = "Quick sync..."
        val lastSyncAt = preferencesRepository.getLastSyncAtOnce()

        // 1. Get new server assets since last sync
        val buckets = immichApi.getTimeBuckets("MONTH")
        var newFromServer = 0

        var consecutiveEmpty = 0
        for (bucket in buckets) {
            coroutineContext.job.ensureActive()
            val bucketData = immichApi.getTimeBucket(bucket.timeBucket, "MONTH")
            val items = bucketData.toDomainList()
            val newItems = items.filter { it.captureDate > lastSyncAt }
            if (newItems.isEmpty()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= 3) break // Buckets are newest-first; 3 old buckets in a row = done
            } else {
                consecutiveEmpty = 0
                val entities = newItems.map { item -> mergeWithExisting(item) }
                mediaDao.upsertAll(entities)
                newFromServer += entities.size
            }
        }

        _progress.value = "Syncing albums..."
        syncAlbums()
        _progress.value = "Syncing people..."
        syncPeople()

        preferencesRepository.setLastSyncAt(System.currentTimeMillis())
        _progress.value = if (newFromServer > 0) "Done: $newFromServer from server" else "Done: up to date"
    }

    // ── Public: standalone upload scan (called by UploadScanWorker) ──

    suspend fun scanAndQueueUploads(): Int {
        // Simple: query Room for all DEVICE-only entries and queue them
        val deviceOnly = mediaDao.getByStatus("DEVICE")
        Timber.d("Upload scan: ${deviceOnly.size} device-only photos found")
        var queued = 0
        for (entry in deviceOnly) {
            val path = entry.localPath ?: continue
            if (!isUploadablePath(path) || entry.filename.contains(".VB-01.COVER.")) {
                // Not uploadable — don't leave as DEVICE or it'll be re-queued forever
                mediaDao.deleteByNasId(entry.nasId)
                continue
            }
            // Check if already in upload queue
            if (uploadQueueDao.existsByPath(path) != null) continue
            uploadQueueDao.insert(UploadQueueEntity(localPath = path, targetFolderId = 0))
            queued++
        }
        Timber.d("Upload scan: $queued new items queued")

        // Re-queue failed uploads (max 5 retries)
        val failed = uploadQueueDao.getFailed()
        for (item in failed) {
            if (item.retryCount < 5) {
                uploadQueueDao.updateStatus(item.id, "PENDING", item.retryCount)
            }
        }

        if (queued > 0 || failed.any { it.retryCount < 5 }) {
            triggerUploadWorker()
        }
        return queued
    }

    // ── Internal: queue new device photos for upload ──────────────

    private suspend fun queueNewDevicePhotosForUpload(photos: List<LocalPhoto>): Int {
        if (photos.isEmpty()) return 0

        var toUpload = 0
        val checksums = photos.map {
            mapOf("id" to it.filename, "checksum" to it.sha1Base64)
        }
        for (batch in checksums.chunked(100)) {
            coroutineContext.job.ensureActive()
            try {
                val result = immichApi.bulkUploadCheck(kotlinx.serialization.json.buildJsonObject {
                    put("assets", kotlinx.serialization.json.JsonArray(batch.map { entry ->
                        kotlinx.serialization.json.buildJsonObject {
                            put("id", kotlinx.serialization.json.JsonPrimitive(entry["id"]!!))
                            put("checksum", kotlinx.serialization.json.JsonPrimitive(entry["checksum"]!!))
                        }
                    }))
                })
                val rejectIds = result.results
                    .filter { it["action"] == "reject" }
                    .mapNotNull { it["id"] }
                    .toSet()

                for (photo in photos) {
                    if (photo.filename in rejectIds) continue
                    if (photo.filename.contains(".VB-01.COVER.")) continue
                    if (!isUploadablePath(photo.path)) continue

                    val existing = mediaDao.getByLocalPath(photo.path)
                    if (existing != null) {
                        // Already on NAS (real UUID nasId) → skip
                        if (existing.nasId.length > 10 && !existing.nasId.startsWith("-")) continue
                        // Already queued for upload → skip
                        if (existing.storageStatus == "DEVICE") continue
                        // Check by hash if it's already on NAS under a different entry
                        val nasEntry = photo.sha1Base64.let { hash -> if (hash.isNotBlank()) mediaDao.getByHash(hash) else null }
                        if (nasEntry != null && nasEntry.nasId != existing.nasId && nasEntry.nasId.length > 10 && !nasEntry.nasId.startsWith("-")) {
                            // Already on NAS — link local file to NAS entry, delete temp entry
                            mediaDao.updateStorageStatus(nasEntry.nasId, "SYNCED", photo.path)
                            if (existing.nasId != nasEntry.nasId) mediaDao.deleteByNasId(existing.nasId)
                            continue
                        }
                        // Device-only with temp nasId → needs upload
                        if (existing.storageStatus == "SYNCED" || existing.storageStatus == "DEVICE") {
                            mediaDao.updateStorageStatus(existing.nasId, "DEVICE", existing.localPath)
                            uploadQueueDao.insert(UploadQueueEntity(localPath = photo.path, targetFolderId = 0))
                            toUpload++
                        }
                        continue
                    }

                    // Check by hash before creating new entry — might already be on NAS
                    val nasByHash = photo.sha1Base64.let { hash -> if (hash.isNotBlank()) mediaDao.getByHash(hash) else null }
                    if (nasByHash != null && nasByHash.nasId.length > 10 && !nasByHash.nasId.startsWith("-")) {
                        // Already on NAS — just link local file
                        mediaDao.updateStorageStatus(nasByHash.nasId, "SYNCED", photo.path)
                        continue
                    }

                    // New photo — create entity + queue
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
                        storageStatus = "DEVICE",
                        nasHash = photo.sha1Base64,
                        localFileModifiedAt = photo.dateModified,
                    )
                    mediaDao.upsert(entity)
                    uploadQueueDao.insert(UploadQueueEntity(localPath = photo.path, targetFolderId = 0))
                    toUpload++
                }
            } catch (e: Exception) {
                Timber.w(e, "Bulk upload check failed, skipping batch")
            }
        }
        // Re-queue failed uploads (max 5 retries)
        val failed = uploadQueueDao.getFailed()
        for (item in failed) {
            if (item.retryCount < 5) {
                uploadQueueDao.updateStatus(item.id, "PENDING", item.retryCount)
                Timber.d("Re-queued failed upload: ${item.localPath} (retry ${item.retryCount + 1})")
            }
        }

        // Trigger UploadWorker to process the queue
        if (toUpload > 0 || failed.any { it.retryCount < 5 }) {
            triggerUploadWorker()
        }

        return toUpload
    }

    private fun triggerUploadWorker() {
        try {
            val wifiOnly = kotlinx.coroutines.runBlocking { preferencesRepository.wifiOnlyUpload.first() }
            val networkType = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
            androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                UploadWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                androidx.work.OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(networkType)
                            .build(),
                    )
                    .build(),
            )
            Timber.d("Upload worker triggered")
        } catch (e: Exception) {
            Timber.w(e, "Failed to trigger upload worker")
        }
    }

    /** Returns false for third-party app directories that should not be uploaded. */
    private fun isUploadablePath(path: String): Boolean {
        if (path.contains("/Android/media/")) return false
        if (path.contains("/WhatsApp/")) return false
        if (path.contains("/Telegram/")) return false
        return true
    }

    // ── Device Photo Scanner (with hash caching) ───────────────

    private suspend fun scanDevicePhotos(): List<LocalPhoto> = scanMediaStore(null)

    private suspend fun scanDevicePhotosSince(sinceMs: Long): List<LocalPhoto> = scanMediaStore(sinceMs)

    private data class PendingPhoto(
        val path: String, val filename: String, val size: Long,
        val captureDate: Long, val isVideo: Boolean, val dateModifiedSec: Long,
        val cachedHash: String?,
    )

    private suspend fun scanMediaStore(sinceMs: Long?): List<LocalPhoto> {
        // Pre-load hash cache from Room: path → (hash, modifiedAt)
        val hashCache = mutableMapOf<String, Pair<String, Long?>>()
        try {
            val cached = mediaDao.getLocalHashCache()
            for (entry in cached) {
                hashCache[entry.localPath] = entry.nasHash to entry.localFileModifiedAt
            }
            Timber.d("Hash cache: ${hashCache.size} entries loaded")
            if (hashCache.size > 50_000) Timber.w("Hash cache very large: ${hashCache.size} entries")
        } catch (e: Exception) {
            Timber.w(e, "Failed to load hash cache")
        }

        // Phase 1: Collect metadata from MediaStore cursor (fast, no I/O)
        val pending = mutableListOf<PendingPhoto>()

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
                    if (!file.exists() || !file.canRead()) continue

                    val size = cursor.getLong(sizeCol)
                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateModifiedSec = cursor.getLong(dateModCol)
                    val dateModified = dateModifiedSec * 1000L
                    val captureDate = if (dateTaken > 0) dateTaken else dateModified

                    val cachedEntry = hashCache[localPath]
                    val cachedHash = if (cachedEntry != null && cachedEntry.second == dateModifiedSec) {
                        cachedEntry.first
                    } else null

                    pending.add(PendingPhoto(localPath, filename, size, captureDate, isVideo, dateModifiedSec, cachedHash))
                }
            }
        }

        // Phase 2: Hash cache-miss files in parallel (4 concurrent I/O threads)
        val hashSemaphore = Semaphore(4)
        val photos = kotlinx.coroutines.coroutineScope {
            pending.map { p ->
                async(Dispatchers.IO) {
                    val sha1: String = if (p.cachedHash != null) {
                        p.cachedHash
                    } else {
                        hashSemaphore.withPermit {
                            try {
                                HashUtil.sha1Base64(File(p.path))
                            } catch (e: Exception) {
                                Timber.w("Failed to hash ${p.filename}: ${e.message}")
                                return@async null
                            }
                        }
                    }
                    LocalPhoto(
                        path = p.path,
                        filename = p.filename,
                        sha1Base64 = sha1,
                        captureDate = p.captureDate,
                        size = p.size,
                        isVideo = p.isVideo,
                        dateModified = p.dateModifiedSec,
                    )
                }
            }.awaitAll().filterNotNull()
        }

        return photos
    }

    // ── Fetch all server assets (parallelized checksums) ───────

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

            for (item in items) {
                coroutineContext.job.ensureActive()
                val entity = mergeWithExisting(item)
                allEntities.add(entity)
            }

            mediaDao.upsertAll(allEntities.takeLast(items.size))
            synced += items.size
        }

        // Fetch checksums in parallel for items that don't have them
        _progress.value = "Fetching checksums..."
        val needChecksum = allEntities.filter { it.nasHash.isNullOrBlank() }
        if (needChecksum.isNotEmpty()) {
            Timber.d("Fetching ${needChecksum.size} checksums in parallel...")
            val semaphore = Semaphore(20)
            var fetched = 0

            for (chunk in needChecksum.chunked(500)) {
                coroutineContext.job.ensureActive()
                val results = withContext(Dispatchers.IO) {
                    chunk.map { entity ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val asset = immichApi.getAsset(entity.nasId)
                                    asset.checksum?.let { entity.nasId to it }
                                } catch (e: Exception) {
                                    Timber.w("Failed to get checksum for ${entity.nasId}")
                                    null
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                // Batch update checksums in Room and in our list
                for ((nasId, checksum) in results) {
                    mediaDao.updateHash(nasId, checksum)
                    val idx = allEntities.indexOfFirst { it.nasId == nasId }
                    if (idx >= 0) {
                        allEntities[idx] = allEntities[idx].copy(nasHash = checksum)
                    }
                }

                fetched += results.size
                _progress.value = "Fetching checksums: $fetched / ${needChecksum.size}..."
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
                val items = assets.mapNotNull { ImmichPhotoMapper.run { it.toDomain() } }
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
