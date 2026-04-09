package dev.egallery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.util.StorageManager
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class EvictionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mediaDao: MediaDao,
    private val storageManager: StorageManager,
    private val preferencesRepository: AppPreferencesRepository,
    private val immichApi: dev.egallery.api.ImmichPhotoService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val autoEvict = preferencesRepository.autoEvictEnabled.first()
        if (!autoEvict) {
            Timber.d("Auto-eviction disabled, skipping")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val rollingCutoff = now - ROLLING_WINDOW_MS

        val expired = mediaDao.getExpiredLocalFiles(rollingCutoff, now)

        var evicted = 0
        for (entity in expired) {
            // Never evict items still pending upload
            if (entity.storageStatus == "DEVICE" || entity.storageStatus == "DEVICE") {
                continue
            }

            val localPath = entity.localPath ?: continue
            storageManager.deleteLocalFile(localPath)
            mediaDao.updateStorageStatus(entity.nasId, "NAS", null)
            evicted++
        }

        if (evicted > 0) {
            Timber.d("Evicted $evicted local files (${expired.size} candidates)")
        }

        // Auto-purge trashed items older than 30 days
        val trashCutoff = now - TRASH_RETENTION_MS
        val expiredTrash = mediaDao.getExpiredTrash(trashCutoff)
        val serverDeleteIds = mutableListOf<String>()
        val deleteIds = mutableListOf<String>()
        for (entity in expiredTrash) {
            entity.localPath?.let { storageManager.deleteLocalFile(it) }
            if (entity.nasId.length > 10 && !entity.nasId.startsWith("-")) {
                serverDeleteIds.add(entity.nasId)
            }
            deleteIds.add(entity.nasId)
        }
        // Batch delete from Room
        for (chunk in deleteIds.chunked(500)) {
            mediaDao.deleteByNasIds(chunk)
        }
        // Delete from Immich server too
        if (serverDeleteIds.isNotEmpty()) {
            try {
                immichApi.deleteAssets(kotlinx.serialization.json.buildJsonObject {
                    put("ids", kotlinx.serialization.json.JsonArray(serverDeleteIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    put("force", kotlinx.serialization.json.JsonPrimitive(true))
                })
                Timber.d("Deleted ${serverDeleteIds.size} items from Immich server")
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete ${serverDeleteIds.size} items from server")
            }
        }
        if (expiredTrash.isNotEmpty()) {
            Timber.d("Permanently deleted ${expiredTrash.size} items from trash")
        }

        // Auto-delete Video Boost COVER files if MAIN exists and COVER is >30 days old
        val autoDeleteCovers = preferencesRepository.autoDeleteCovers.first()
        if (autoDeleteCovers) {
            val dcim = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DCIM,
            ).resolve("Camera")
            val coverFiles = dcim.listFiles()?.filter { it.name.contains(".VB-01.COVER.") } ?: emptyList()
            var deletedCovers = 0
            for (cover in coverFiles) {
                val mainName = cover.name.replace(".VB-01.COVER.", ".VB-02.MAIN.")
                val mainFile = java.io.File(dcim, mainName)
                if (mainFile.exists() && cover.lastModified() < now - COVER_RETENTION_MS) {
                    cover.delete()
                    // Also remove from DB if exists
                    val entity = mediaDao.getByLocalPath(cover.absolutePath)
                    if (entity != null) mediaDao.deleteByNasId(entity.nasId)
                    deletedCovers++
                }
            }
            if (deletedCovers > 0) {
                Timber.d("Deleted $deletedCovers Video Boost COVER files")
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "eviction"
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val ROLLING_WINDOW_MS = 365L * 24 * 60 * 60 * 1000 // 1 year
        private const val COVER_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}
