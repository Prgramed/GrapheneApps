package dev.egallery.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.egallery.data.CredentialStore
import dev.egallery.util.MemoryVideoEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class MemoryVideoExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val credentialStore: CredentialStore,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val assetIds = inputData.getStringArray(KEY_ASSET_IDS) ?: return Result.failure()
        if (assetIds.isEmpty()) return Result.failure()

        createNotificationChannel()
        setForeground(createForegroundInfo("Preparing memory video..."))

        val serverUrl = credentialStore.serverUrl.trimEnd('/')
        val cacheDir = File(applicationContext.cacheDir, "memory_export").apply { mkdirs() }

        try {
            // Step 1: Download preview images
            val bitmaps = mutableListOf<android.graphics.Bitmap>()
            for ((index, assetId) in assetIds.withIndex()) {
                setForeground(createForegroundInfo("Downloading ${index + 1}/${assetIds.size}..."))

                val url = "$serverUrl/api/assets/$assetId/thumbnail?size=preview"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }
                val bytes = response.body?.bytes() ?: continue
                response.close()

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                bitmaps.add(bitmap)
            }

            if (bitmaps.isEmpty()) return Result.failure()

            // Step 2: Pick random ambient track
            val audioResId = (1..9).mapNotNull { i ->
                val resId = applicationContext.resources.getIdentifier("memory_ambient_$i", "raw", applicationContext.packageName)
                if (resId != 0) resId else null
            }.shuffled().firstOrNull() ?: 0

            // Step 3: Encode video
            val tempFile = File(cacheDir, "memory_export.mp4")
            setForeground(createForegroundInfo("Encoding video..."))

            MemoryVideoEncoder.encode(
                context = applicationContext,
                bitmaps = bitmaps,
                audioResId = audioResId,
                outputFile = tempFile,
                onProgress = { current, total ->
                    setForegroundAsync(createForegroundInfo("Encoding $current/$total photos..."))
                },
            )

            // Recycle bitmaps
            bitmaps.forEach { it.recycle() }

            // Step 4: Save to MediaStore
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val displayName = "Memory_$dateStr.mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/eGallery")
            }
            val uri = applicationContext.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
            )

            if (uri != null) {
                applicationContext.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { it.copyTo(out) }
                }
                Timber.d("Memory video saved: $uri")
            }

            // Cleanup temp
            tempFile.delete()
            cacheDir.delete()

            // Show completion notification
            showCompletionNotification(uri)

            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Memory video export failed")
            cacheDir.deleteRecursively()
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Memory Export",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Memory video export progress" }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Exporting Memory Video")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun showCompletionNotification(videoUri: android.net.Uri?) {
        val intent = if (videoUri != null) {
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(videoUri, "video/mp4")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else null

        val pendingIntent = intent?.let {
            android.app.PendingIntent.getActivity(
                applicationContext, 0, it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Memory Video Saved")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        const val KEY_ASSET_IDS = "asset_ids"
        private const val CHANNEL_ID = "memory_export"
        private const val NOTIFICATION_ID = 9001

        fun enqueue(context: Context, assetIds: List<String>) {
            val data = workDataOf(KEY_ASSET_IDS to assetIds.toTypedArray())
            val request = OneTimeWorkRequestBuilder<MemoryVideoExportWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
