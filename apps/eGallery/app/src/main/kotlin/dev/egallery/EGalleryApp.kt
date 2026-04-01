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
                for (status in listOf("UPLOAD_FAILED", "UPLOAD_PENDING")) {
                    val items = mediaDao.getByStorageStatus(status)
                    for (entity in items) {
                        val path = entity.localPath ?: continue
                        if (path.contains("/Android/media/") || path.contains("/WhatsApp/") || path.contains("/Telegram/")) {
                            mediaDao.updateStorageStatus(entity.nasId, "ON_DEVICE", path)
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
