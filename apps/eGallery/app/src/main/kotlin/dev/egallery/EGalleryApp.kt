package dev.egallery

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

import dev.egallery.data.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EGalleryApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var credentialStore: CredentialStore
    @Inject lateinit var mediaDao: dev.egallery.data.db.dao.MediaDao
    @Inject lateinit var uploadQueueDao: dev.egallery.data.db.dao.UploadQueueDao
    @Inject lateinit var syncEngine: dev.egallery.sync.NasSyncEngine
    @Inject lateinit var prefsRepo: dev.egallery.data.preferences.AppPreferencesRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        installCrashHandler()
        clearPoisonedCache()
        restoreWronglyTrashedItems()
        deduplicateEntries()

        // Auto-sync on startup (fetch from server)
        appScope.launch {
            kotlinx.coroutines.delay(3000)
            if (credentialStore.serverUrl.isNotBlank()) {
                syncEngine.startQuickSync()
            }
        }

        // Auto-upload scan on startup (detect new device photos + queue)
        appScope.launch {
            kotlinx.coroutines.delay(5000)
            if (credentialStore.serverUrl.isNotBlank()) {
                try {
                    syncEngine.scanAndQueueUploads()
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Startup upload scan failed")
                }
            }
        }

        // Schedule periodic upload scan (every 30 min) if auto-upload is on
        schedulePeriodicUploadScan()
    }

    private fun schedulePeriodicUploadScan() {
        appScope.launch {
            val autoUpload = prefsRepo.autoUploadEnabled.first()
            val wm = androidx.work.WorkManager.getInstance(this@EGalleryApp)
            if (autoUpload) {
                val wifiOnly = prefsRepo.wifiOnlyUpload.first()
                val networkType = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
                val request = androidx.work.PeriodicWorkRequestBuilder<dev.egallery.sync.UploadScanWorker>(
                    30, java.util.concurrent.TimeUnit.MINUTES,
                ).setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build(),
                ).build()
                wm.enqueueUniquePeriodicWork(
                    dev.egallery.sync.UploadScanWorker.PERIODIC_WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
                timber.log.Timber.d("Periodic upload scan scheduled (every 30 min)")
            } else {
                wm.cancelUniqueWork(dev.egallery.sync.UploadScanWorker.PERIODIC_WORK_NAME)
            }
        }
    }

    /** Merge duplicate entries — by hash and by filename — runs every startup */
    private fun deduplicateEntries() {
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var merged = 0

                // Pass 1: Dedup by hash — skip if none
                val hashDupes = mediaDao.findHashDuplicates()
                if (hashDupes.isNotEmpty()) {
                    Timber.d("Found ${hashDupes.size} hash duplicates to merge")
                    for (hash in hashDupes) {
                        val entries = mediaDao.getAllByHash(hash)
                        if (entries.size < 2) continue
                        merged += mergeEntries(entries)
                    }
                }

                // Pass 2: Dedup by filename — skip if none
                val filenameDupes = mediaDao.findFilenameDuplicates()
                if (filenameDupes.isNotEmpty()) {
                    Timber.d("Found ${filenameDupes.size} filename duplicates to merge")
                    for (filename in filenameDupes) {
                        val entries = mediaDao.getAllByFilename(filename)
                        if (entries.size < 2) continue
                        val hasReal = entries.any { isRealNasId(it.nasId) }
                        val hasTemp = entries.any { !isRealNasId(it.nasId) }
                        if (hasReal && hasTemp) {
                            merged += mergeEntries(entries)
                        }
                    }
                }

                if (merged > 0) Timber.d("Deduplicated $merged entries")
            } catch (e: Exception) {
                Timber.w(e, "Dedup failed")
            }
        }
    }

    private suspend fun mergeEntries(entries: List<dev.egallery.data.db.entity.MediaEntity>): Int {
        // Prefer: real nasId > has localPath > newest captureDate
        val best = entries.sortedWith(compareByDescending<dev.egallery.data.db.entity.MediaEntity> {
            isRealNasId(it.nasId)
        }.thenByDescending {
            it.localPath != null
        }.thenByDescending {
            it.captureDate
        }).first()
        // Merge localPath from any entry into the best one
        val localPath = entries.firstOrNull { it.localPath != null }?.localPath
        if (localPath != null && best.localPath == null) {
            mediaDao.updateStorageStatus(best.nasId, "SYNCED", localPath)
        }
        var count = 0
        for (entry in entries) {
            if (entry.nasId != best.nasId) {
                mediaDao.deleteByNasId(entry.nasId)
                count++
            }
        }
        return count
    }

    private fun isRealNasId(nasId: String): Boolean {
        return nasId.length > 10 && !nasId.startsWith("-") && nasId.toIntOrNull() == null
    }

    private fun clearPoisonedCache() {
        val prefs = getSharedPreferences("cache_version", MODE_PRIVATE)
        if (prefs.getInt("clear_v", 0) < 5) {
            cacheDir.resolve("thumbnails").deleteRecursively()
            prefs.edit().putInt("clear_v", 5).apply()
            Timber.d("Cleared poisoned thumbnail cache v3")
        }
    }

    private fun restoreWronglyTrashedItems() {
        val prefs = getSharedPreferences("cache_version", MODE_PRIVATE)
        // One-time: restore items wrongly trashed by incomplete sync reconciliation
        if (prefs.getInt("restore_v", 0) < 2) {
            appScope.launch {
                val count = mediaDao.restoreAllTrash()
                Timber.d("Restored $count wrongly-trashed items")
                prefs.edit().putInt("restore_v", 2).apply()
            }
        }
        // One-time: reset old thumbnail markers so prefetch worker re-downloads them
        if (prefs.getInt("thumb_reset_v", 0) < 1) {
            appScope.launch {
                val count = mediaDao.resetStaleThumbnailPaths()
                Timber.d("Reset $count stale thumbnail paths for re-download")
                prefs.edit().putInt("thumb_reset_v", 1).apply()
            }
        }
        // Reset "none" thumbnails so they retry with Streaming API fallback
        if (prefs.getInt("thumb_reset_v", 0) < 3) {
            appScope.launch {
                val count = mediaDao.resetStaleThumbnailPaths()
                Timber.d("Reset $count thumbnails for Streaming API retry")
                prefs.edit().putInt("thumb_reset_v", 3).apply()
            }
        }
        // One-time: remove Live Photo MOV duplicates (misidentified as PHOTO)
        if (prefs.getInt("live_cleanup_v", 0) < 1) {
            appScope.launch {
                val count = mediaDao.deleteLivePhotoMovDuplicates()
                Timber.d("Removed $count Live Photo MOV duplicates")
                prefs.edit().putInt("live_cleanup_v", 1).apply()
            }
        }
        // One-time: clear upload queue for third-party app directories (WhatsApp, Telegram, etc.)
        if (prefs.getInt("clear_bad_uploads_v", 0) < 2) {
            appScope.launch {
                var cleared = 0
                for (pattern in listOf("/Android/media/", "/WhatsApp/", "/Telegram/")) {
                    cleared += uploadQueueDao.deleteByPathContaining(pattern)
                }
                // Reset UPLOAD_FAILED/UPLOAD_PENDING to ON_DEVICE for these paths
                for (status in listOf("DEVICE", "DEVICE")) {
                    val items = mediaDao.getByStorageStatus(status)
                    for (entity in items) {
                        val path = entity.localPath ?: continue
                        if (path.contains("/Android/media/") || path.contains("/WhatsApp/") || path.contains("/Telegram/")) {
                            mediaDao.updateStorageStatus(entity.nasId, "SYNCED", path)
                            cleared++
                        }
                    }
                }
                Timber.d("Cleared $cleared third-party app upload entries")
                prefs.edit().putInt("clear_bad_uploads_v", 2).apply()
            }
        }
    }


    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                add(coil3.video.VideoFrameDecoder.Factory())
            }
            .logger(object : coil3.util.Logger {
                override var minLevel = coil3.util.Logger.Level.Warn
                override fun log(tag: String, level: coil3.util.Logger.Level, message: String?, throwable: Throwable?) {
                    if (throwable != null) Timber.w(throwable, "Coil [$tag]: $message")
                    else Timber.w("Coil [$tag]: $message")
                }
            })
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnails"))
                    .maxSizeBytes(500L * 1024 * 1024) // 500 MB
                    .build()
            }
            .build()

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = java.io.File(filesDir, "crash.log")
                logFile.appendText(
                    buildString {
                        appendLine("--- Crash at ${java.util.Date()} ---")
                        appendLine("Thread: ${thread.name}")
                        appendLine(throwable.stackTraceToString())
                        appendLine()
                    },
                )
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
