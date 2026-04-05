package dev.egallery.sync

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val tempIdCounter = AtomicInteger(-10000)

    suspend fun forceRescan() {
        preferencesRepository.setLastDeviceScanAt(0L)
        scanIfNeeded()
    }

    /**
     * Lightweight scan: queries MediaStore newest-first and stops when hitting
     * files already in Room. No hashing — just inserts so they appear in timeline.
     */
    suspend fun quickScanRecentFiles() = withContext(Dispatchers.IO) {
        var imported = 0
        for ((uri, mediaType) in listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "PHOTO",
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "VIDEO",
        )) {
            imported += scanMediaStoreUntilKnown(uri, mediaType)
        }
        if (imported > 0) Timber.d("Quick scan: imported $imported new files")
        imported
    }

    private suspend fun scanMediaStoreUntilKnown(
        uri: android.net.Uri,
        mediaType: String,
    ): Int {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        var imported = 0
        var consecutiveKnown = 0
        try {
            context.contentResolver.query(
                uri, projection,
                null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val filename = cursor.getString(nameCol) ?: continue
                    if (filename.contains(".VB-01.COVER.")) continue

                    var localPath: String? = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (localPath.isNullOrBlank() && relPathCol >= 0) {
                        val relPath = cursor.getString(relPathCol)
                        if (!relPath.isNullOrBlank()) {
                            localPath = "/storage/emulated/0/$relPath$filename"
                        }
                    }
                    if (localPath == null) continue

                    // Already in Room? Count consecutive known files to detect "caught up"
                    if (mediaDao.getByLocalPath(localPath) != null) {
                        consecutiveKnown++
                        // After 20 consecutive known files, we're caught up
                        if (consecutiveKnown >= 20) break
                        continue
                    }
                    consecutiveKnown = 0

                    val fileSize = cursor.getLong(sizeCol)
                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateModified = cursor.getLong(dateModCol) * 1000L
                    val captureDate = if (dateTaken > 0) dateTaken else dateModified

                    val tempNasId = tempIdCounter.getAndDecrement().toString()
                    val entity = MediaEntity(
                        nasId = tempNasId,
                        filename = filename,
                        captureDate = captureDate,
                        fileSize = fileSize,
                        mediaType = mediaType,
                        folderId = 0,
                        cacheKey = "",
                        localPath = localPath,
                        storageStatus = "DEVICE",
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                    mediaDao.upsert(entity)
                    imported++
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Quick scan failed for $mediaType")
        }
        return imported
    }

    suspend fun scanIfNeeded() {
        val lastScan = preferencesRepository.getLastDeviceScanAt()
        if (System.currentTimeMillis() - lastScan < 6 * 60 * 60 * 1000L) return

        withContext(Dispatchers.IO) {
            val images = scanMediaStore(
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaType = "PHOTO",
            )
            val videos = scanMediaStore(
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                mediaType = "VIDEO",
            )
            preferencesRepository.setLastDeviceScanAt(System.currentTimeMillis())

            val totalImported = images + videos
            Timber.d("Device scan complete: $images images + $videos videos imported")
        }
    }

    private suspend fun scanMediaStore(
        uri: android.net.Uri,
        mediaType: String,
    ): Int {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        var imported = 0

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idCol)
                    val filename = cursor.getString(nameCol) ?: continue

                    // Skip Video Boost COVER files if MAIN exists
                    if (filename.contains(".VB-01.COVER.")) {
                        val mainName = filename.replace(".VB-01.COVER.", ".VB-02.MAIN.")
                        if (mediaDao.searchByFilename(mainName).isNotEmpty()) continue
                    }

                    // Build localPath from DATA column, or construct from RELATIVE_PATH
                    var localPath: String? = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (localPath.isNullOrBlank() && relPathCol >= 0) {
                        val relPath = cursor.getString(relPathCol)
                        if (!relPath.isNullOrBlank()) {
                            localPath = "/storage/emulated/0/$relPath$filename"
                        }
                    }

                    // Use content URI as fallback identifier
                    val contentUri = ContentUris.withAppendedId(uri, mediaStoreId).toString()
                    val identifierPath = localPath ?: contentUri

                    // Skip if already in Room (by localPath or filename)
                    if (mediaDao.getByLocalPath(identifierPath) != null) continue
                    // Also skip if a NAS-synced item with same filename exists (nasId > 0)
                    val existingByName = mediaDao.searchByFilename(filename)
                    if (existingByName.any { (it.nasId.toIntOrNull() ?: 0) > 0 }) continue

                    // Check for NAS duplicate by hash (if file is accessible)
                    var nasHash: String? = null
                    val isUploadable = localPath != null && !localPath.startsWith("content://") && File(localPath).exists()
                    if (isUploadable) {
                        nasHash = try { HashUtil.sha1Base64(File(localPath)) } catch (_: Exception) { null }
                        // If hash matches an existing NAS item, mark as ON_DEVICE (already on NAS)
                        if (nasHash != null) {
                            val existingByHash = mediaDao.getByHash(nasHash)
                            if (existingByHash != null) {
                                // Photo already exists on NAS — update local path on the existing entry
                                mediaDao.updateStorageStatus(existingByHash.nasId, "SYNCED", localPath)
                                imported++
                                continue
                            }
                        }
                    }

                    val fileSize = cursor.getLong(sizeCol)
                    val dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateModified = cursor.getLong(dateModCol) * 1000L
                    val captureDate = if (dateTaken > 0) dateTaken else dateModified

                    val tempNasId = tempIdCounter.getAndDecrement().toString()

                    // Device scanner: always ON_DEVICE. Only CameraWatcher sets UPLOAD_PENDING.
                    val entity = MediaEntity(
                        nasId = tempNasId,
                        filename = filename,
                        captureDate = captureDate,
                        fileSize = fileSize,
                        mediaType = mediaType,
                        folderId = 0,
                        cacheKey = "",
                        localPath = identifierPath,
                        storageStatus = "DEVICE",
                        nasHash = nasHash,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                    mediaDao.upsert(entity)

                    imported++
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "MediaStore scan failed for $mediaType")
        }

        return imported
    }
}
