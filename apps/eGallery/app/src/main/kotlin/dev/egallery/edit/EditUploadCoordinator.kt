package dev.egallery.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.MediaRepository
import dev.egallery.data.repository.toEntity
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.StorageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditUploadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val mediaDao: MediaDao,
    private val mediaRepository: MediaRepository,
) {
    private val tempDir: File
        get() = File(context.cacheDir, "edit_temp").also { it.mkdirs() }

    suspend fun downloadOriginal(item: MediaItem): File = withContext(Dispatchers.IO) {
        // If already on device, use local file
        if (item.storageStatus == StorageStatus.SYNCED && item.localPath != null) {
            return@withContext File(item.localPath)
        }

        val response = immichApi.downloadOriginal(item.nasId)
        val destFile = File(tempDir, "${item.nasId}_${item.filename}")

        response.byteStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }

        Timber.d("Downloaded original for editing: ${destFile.name}")
        destFile
    }

    fun decodeBitmap(file: File): Bitmap {
        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } catch (e: Exception) {
            Timber.w(e, "ImageDecoder failed, falling back to BitmapFactory")
            BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("Failed to decode image: ${file.name}")
        }
    }

    suspend fun saveAndUpload(
        item: MediaItem,
        editedBitmap: Bitmap,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save edited bitmap as JPEG
            val outputFile = File(tempDir, "edited_${item.nasId}.jpg")
            PhotoEditor.save(editedBitmap, outputFile)

            // Upload to Immich as new asset
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date(item.captureDate))
            val requestBody = outputFile.asRequestBody("image/jpeg".toMediaType())
            val filePart = MultipartBody.Part.createFormData("assetData", item.filename, requestBody)
            val params = mapOf(
                "deviceAssetId" to item.filename.toRequestBody(null),
                "deviceId" to "eGallery-android".toRequestBody(null),
                "fileCreatedAt" to dateStr.toRequestBody(null),
                "fileModifiedAt" to dateStr.toRequestBody(null),
            )

            val uploadResponse = immichApi.uploadAsset(filePart, params)
            val newNasId = uploadResponse.id

            // Update local DB with new asset ID
            val merged = item.copy(
                nasId = newNasId,
                cacheKey = newNasId,
                lastSyncedAt = System.currentTimeMillis(),
            )
            mediaDao.upsert(merged.toEntity())

            // Cleanup temp files
            outputFile.delete()
            File(tempDir, "${item.nasId}_${item.filename}").delete()

            Timber.d("Edit pipeline complete for nasId=${item.nasId} -> newNasId=$newNasId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Edit pipeline failed for nasId=${item.nasId}")
            Result.failure(e)
        }
    }

    fun cleanupTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
}
