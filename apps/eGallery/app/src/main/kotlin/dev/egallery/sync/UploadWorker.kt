package dev.egallery.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val mediaDao: MediaDao,
    private val uploadQueueDao: UploadQueueDao,
    private val uploadStatus: UploadStatus,
    private val preferencesRepository: AppPreferencesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo("Starting upload..."))

        uploadStatus.setRunning(true)
        uploadStatus.update("Starting upload...")
        val serverUrl = credentialStore.serverUrl
        if (serverUrl.isBlank()) {
            uploadStatus.update("No server configured")
            uploadStatus.setRunning(false)
            return Result.failure()
        }

        var pending = uploadQueueDao.getPending()

        if (pending.isEmpty()) {
            uploadStatus.update("No pending uploads")
            uploadStatus.setRunning(false)
            return Result.success()
        }

        // Pre-filter: bulk-check which files are already on server and skip them
        uploadStatus.update("Checking ${pending.size} files...")
        pending = preFilterDuplicates(pending)

        if (pending.isEmpty()) {
            uploadStatus.update("All files already on server")
            uploadStatus.setRunning(false)
            return Result.success()
        }

        val concurrency = preferencesRepository.getUploadConcurrencyOnce().coerceIn(1, 8)
        Timber.d("Uploading ${pending.size} items with concurrency=$concurrency")

        val semaphore = Semaphore(concurrency)
        val uploaded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val total = pending.size

        uploadStatus.update("Uploading 0/$total...")

        coroutineScope {
            for (item in pending) {
                ensureActive()
                launch {
                    semaphore.withPermit {
                        ensureActive()
                        uploadSingleItem(item, uploaded, failed, total)
                    }
                }
            }
        }

        val uploadedCount = uploaded.get()
        val failedCount = failed.get()
        Timber.d("Upload batch: $uploadedCount uploaded, $failedCount failed")
        val statusMsg = if (failedCount > 0) "Done: $uploadedCount uploaded, $failedCount failed" else "Done: $uploadedCount uploaded"
        uploadStatus.update(statusMsg)
        uploadStatus.setRunning(false)

        if (uploadedCount > 0) {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                NasSyncWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<NasSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
        }

        return if (failedCount > 0) Result.retry() else Result.success()
    }

    private suspend fun uploadSingleItem(
        item: dev.egallery.data.db.entity.UploadQueueEntity,
        uploaded: AtomicInteger,
        failed: AtomicInteger,
        total: Int,
    ) {
        Timber.d("UploadWorker: processing ${item.localPath} (status=${item.status})")
        val file = File(item.localPath)
        if (!file.exists()) {
            Timber.w("Upload file missing: ${item.localPath}, removing from queue")
            uploadQueueDao.delete(item.id)
            mediaDao.getByLocalPath(item.localPath)?.let {
                mediaDao.deleteByNasId(it.nasId)
            }
            return
        }

        // Check if file is actually readable — WhatsApp/scoped-storage files may not be
        val isReadable = try {
            file.inputStream().use { it.read(); true }
        } catch (_: Exception) { false }
        if (!isReadable) {
            Timber.w("Upload file not readable (scoped storage): ${item.localPath}, removing")
            uploadQueueDao.delete(item.id)
            mediaDao.getByLocalPath(item.localPath)?.let {
                // Keep in timeline (ON_DEVICE) but don't try to upload
                if (it.storageStatus == "UPLOAD_PENDING" || it.storageStatus == "UPLOAD_FAILED") {
                    mediaDao.updateStorageStatus(it.nasId, "ON_DEVICE", it.localPath)
                }
            }
            return
        }

        try {
            val entity = mediaDao.getByLocalPath(item.localPath)
            val captureDate = entity?.captureDate ?: System.currentTimeMillis()
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date(captureDate))

            val mediaType = guessMediaType(file.name)
            val requestBody = file.asRequestBody(mediaType.toMediaType())
            val filePart = MultipartBody.Part.createFormData("assetData", file.name, requestBody)
            val params = mapOf(
                "deviceAssetId" to file.name.toRequestBody(null),
                "deviceId" to "eGallery-android".toRequestBody(null),
                "fileCreatedAt" to dateStr.toRequestBody(null),
                "fileModifiedAt" to dateStr.toRequestBody(null),
            )

            val response = api.uploadAsset(filePart, params)

            val tempNasId = entity?.nasId ?: ""
            if (tempNasId.isNotBlank()) {
                mediaDao.updateNasIdAndStatus(tempNasId, response.id, "ON_DEVICE")
            }

            uploadQueueDao.delete(item.id)
            val count = uploaded.incrementAndGet()
            val msg = "Uploaded $count/$total: ${file.name}"
            uploadStatus.update(msg)
            // Throttle notification updates — every 5 uploads or on last item
            if (count % 5 == 0 || count == total) {
                setForeground(createForegroundInfo("Uploaded $count/$total"))
            }
            Timber.d("Uploaded ${file.name} -> immichId=${response.id}")
        } catch (e: Exception) {
            Timber.e(e, "Upload failed for ${file.name}")
            handleFailure(item)
            failed.incrementAndGet()
        }
    }

    /**
     * Bulk-check pending files against Immich server. Files already on server
     * are removed from the queue instantly (no upload needed).
     */
    private suspend fun preFilterDuplicates(
        pending: List<dev.egallery.data.db.entity.UploadQueueEntity>,
    ): List<dev.egallery.data.db.entity.UploadQueueEntity> {
        val remaining = pending.toMutableList()
        try {
            for (batch in pending.chunked(100)) {
                val checksums = batch.mapNotNull { item ->
                    val file = File(item.localPath)
                    if (!file.exists() || !file.canRead()) return@mapNotNull null
                    val entity = mediaDao.getByLocalPath(item.localPath)
                    val hash = entity?.nasHash
                    if (hash != null) {
                        mapOf("id" to file.name, "checksum" to hash)
                    } else {
                        mapOf("id" to file.name, "checksum" to "")
                    }
                }
                if (checksums.isEmpty()) continue

                val result = api.bulkUploadCheck(kotlinx.serialization.json.buildJsonObject {
                    put("assets", kotlinx.serialization.json.JsonArray(checksums.map { entry ->
                        kotlinx.serialization.json.buildJsonObject {
                            put("id", kotlinx.serialization.json.JsonPrimitive(entry["id"]!!))
                            put("checksum", kotlinx.serialization.json.JsonPrimitive(entry["checksum"]!!))
                        }
                    }))
                })
                val rejectNames = result.results
                    .filter { it["action"] == "reject" }
                    .mapNotNull { it["id"] }
                    .toSet()

                for (item in batch) {
                    val filename = File(item.localPath).name
                    if (filename in rejectNames) {
                        // Already on server — remove from queue, mark as ON_DEVICE
                        uploadQueueDao.delete(item.id)
                        mediaDao.getByLocalPath(item.localPath)?.let { entity ->
                            mediaDao.updateStorageStatus(entity.nasId, "ON_DEVICE", entity.localPath)
                        }
                        remaining.remove(item)
                        Timber.d("Skipped duplicate: $filename")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Bulk upload check failed, uploading all")
        }
        val skipped = pending.size - remaining.size
        if (skipped > 0) {
            Timber.d("Pre-filter: skipped $skipped duplicates, ${remaining.size} to upload")
            uploadStatus.update("Skipped $skipped duplicates, uploading ${remaining.size}...")
        }
        return remaining
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val channelId = "upload_channel"
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Uploads", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }

        val cancelIntent = WorkManager.getInstance(appContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("eGallery Upload")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun handleFailure(item: dev.egallery.data.db.entity.UploadQueueEntity) {
        // Fail permanently — no retries within the same batch.
        // User can manually "Retry Failed" from Settings if needed.
        uploadQueueDao.updateStatus(item.id, "FAILED", item.retryCount + 1)
        val entity = mediaDao.getByLocalPath(item.localPath)
        if (entity != null) {
            mediaDao.updateStorageStatus(entity.nasId, "UPLOAD_FAILED", entity.localPath)
        }
        Timber.w("Upload failed: ${item.localPath}")
    }

    private fun guessMediaType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic", "heif" -> "image/heif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val WORK_NAME = "upload"
        private const val MAX_RETRIES = 3
        private const val NOTIFICATION_ID = 2001
    }
}
