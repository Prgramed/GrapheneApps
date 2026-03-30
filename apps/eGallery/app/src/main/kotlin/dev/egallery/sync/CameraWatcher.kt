package dev.egallery.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.data.db.entity.UploadQueueEntity
import dev.egallery.util.HashUtil
import dev.egallery.util.MediaFileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class CameraWatcher : Service() {

    @Inject lateinit var mediaDao: MediaDao
    @Inject lateinit var uploadQueueDao: UploadQueueDao
    @Inject lateinit var preferencesRepository: AppPreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val tempIdCounter = AtomicInteger(-1)
    private var fileObserver: FileObserver? = null
    private val pendingFiles = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fileObserver?.stopWatching()
        fileObserver = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startWatching() {
        val cameraDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            .resolve("Camera")

        if (!cameraDir.exists()) {
            Timber.w("Camera directory does not exist: $cameraDir")
            return
        }

        try {
        fileObserver = object : FileObserver(cameraDir, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!MediaFileUtil.isMediaFile(path)) return

                synchronized(pendingFiles) {
                    if (!pendingFiles.add(path)) return // already debouncing
                }

                val file = File(cameraDir, path)
                handler.postDelayed({
                    synchronized(pendingFiles) { pendingFiles.remove(path) }
                    onNewMediaFile(file)
                }, DEBOUNCE_MS)
            }
        }

        fileObserver?.startWatching()
        Timber.d("Watching camera directory: $cameraDir")
        } catch (e: SecurityException) {
            Timber.e(e, "No permission to watch camera directory")
        }
    }

    private fun onNewMediaFile(file: File) {
        if (!file.exists() || file.length() == 0L) return

        // Skip Video Boost COVER files — only upload MAIN
        if (file.name.contains(".VB-01.COVER.")) {
            Timber.d("Skipping Video Boost COVER file: ${file.name}")
            return
        }

        scope.launch {
            try {
                // Hash-based dedup — skip if already in Room
                val hash = HashUtil.sha256(file)
                val existing = mediaDao.getByHash(hash)
                if (existing != null) {
                    Timber.d("Skipping duplicate file: ${file.name} (hash matches nasId=${existing.nasId})")
                    return@launch
                }

                val captureDate = MediaFileUtil.extractCaptureDate(file)
                val mediaType = MediaFileUtil.mediaTypeFromFile(file.name)
                val tempNasId = tempIdCounter.getAndDecrement().toString()

                val entity = MediaEntity(
                    nasId = tempNasId,
                    filename = file.name,
                    captureDate = captureDate,
                    fileSize = file.length(),
                    mediaType = mediaType.name,
                    folderId = 0,
                    cacheKey = "",
                    localPath = file.absolutePath,
                    storageStatus = "UPLOAD_PENDING",
                    nasHash = hash,
                    lastSyncedAt = System.currentTimeMillis(),
                )
                mediaDao.upsert(entity)

                val queueItem = UploadQueueEntity(
                    localPath = file.absolutePath,
                    targetFolderId = 0, // default upload folder, configured in settings
                )
                uploadQueueDao.insert(queueItem)
                enqueueUploadWorker()

                Timber.d("New camera file queued: ${file.name} (tempNasId=$tempNasId)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to process camera file: ${file.name}")
            }
        }
    }

    private suspend fun enqueueUploadWorker() {
        val wifiOnly = preferencesRepository.wifiOnlyUpload.first()
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueue(request)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Camera Watcher",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Watching for new photos"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("eGallery")
            .setContentText("Watching for new photos")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camera_watcher"
        private const val DEBOUNCE_MS = 2000L
    }
}
