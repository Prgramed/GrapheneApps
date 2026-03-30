package dev.egallery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val mediaDao: MediaDao,
    private val uploadQueueDao: UploadQueueDao,
    private val uploadStatus: UploadStatus,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        uploadStatus.setRunning(true)
        uploadStatus.update("Starting upload...")
        val serverUrl = credentialStore.serverUrl
        if (serverUrl.isBlank()) {
            uploadStatus.update("No server configured")
            uploadStatus.setRunning(false)
            return Result.failure()
        }

        val pending = uploadQueueDao.getPending()

        if (pending.isEmpty()) {
            uploadStatus.update("No pending uploads")
            uploadStatus.setRunning(false)
            return Result.success()
        }

        uploadStatus.update("Uploading 0/${pending.size}...")

        var uploaded = 0
        var failed = 0

        for (item in pending) {
            Timber.d("UploadWorker: processing ${item.localPath} (status=${item.status})")
            val file = File(item.localPath)
            if (!file.exists()) {
                Timber.w("Upload file missing: ${item.localPath}, removing from queue")
                uploadQueueDao.delete(item.id)
                // Also clean up the temp media entity
                mediaDao.getByLocalPath(item.localPath)?.let {
                    mediaDao.deleteByNasId(it.nasId)
                }
                continue
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
                    // Update in-place: swap temp ID for real Immich UUID, keep localPath for thumbnail
                    mediaDao.updateNasIdAndStatus(tempNasId, response.id, "ON_DEVICE")
                }

                uploadQueueDao.delete(item.id)
                uploaded++
                uploadStatus.update("Uploaded $uploaded/${pending.size}: ${file.name}")
                Timber.d("Uploaded ${file.name} -> immichId=${response.id}")
            } catch (e: Exception) {
                Timber.e(e, "Upload failed for ${file.name}")
                handleFailure(item)
                failed++
            }
        }

        Timber.d("Upload batch: $uploaded uploaded, $failed failed")
        val statusMsg = if (failed > 0) "Done: $uploaded uploaded, $failed failed" else "Done: $uploaded uploaded"
        uploadStatus.update(statusMsg)
        uploadStatus.setRunning(false)

        // Trigger full sync to pull uploaded items from Immich with real UUIDs
        if (uploaded > 0) {
            val syncRequest = OneTimeWorkRequestBuilder<NasSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(appContext).enqueue(syncRequest)
        }

        return if (failed > 0) Result.retry() else Result.success()
    }

    private suspend fun handleFailure(item: dev.egallery.data.db.entity.UploadQueueEntity) {
        val newRetryCount = item.retryCount + 1
        if (newRetryCount >= MAX_RETRIES) {
            uploadQueueDao.updateStatus(item.id, "FAILED", newRetryCount)
            val entity = mediaDao.getByLocalPath(item.localPath)
            if (entity != null) {
                // Preserve localPath so thumbnail still shows
                mediaDao.updateStorageStatus(entity.nasId, "UPLOAD_FAILED", entity.localPath)
            }
            Timber.w("Upload permanently failed after $MAX_RETRIES retries: ${item.localPath}")
        } else {
            uploadQueueDao.updateStatus(item.id, "PENDING", newRetryCount)
        }
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
    }
}
