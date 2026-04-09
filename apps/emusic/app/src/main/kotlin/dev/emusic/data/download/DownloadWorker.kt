package dev.emusic.data.download

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.playback.NotificationHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val trackDao: TrackDao,
    private val urlBuilder: SubsonicUrlBuilder,
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val artistId = inputData.getString(KEY_ARTIST_ID) ?: return Result.failure()
        val albumId = inputData.getString(KEY_ALBUM_ID) ?: return Result.failure()
        val suffix = inputData.getString(KEY_SUFFIX) ?: "mp3"

        val trackEntity = trackDao.getById(trackId)
        val trackTitle = trackEntity?.title ?: trackId

        val destFile = StoragePaths.trackFile(applicationContext, artistId, albumId, trackId, suffix)
        destFile.parentFile?.mkdirs()

        val notificationId = trackId.hashCode()

        return try {
            setForeground(createProgressForegroundInfo(notificationId, trackTitle, albumId, 0))
            downloadTrack(trackId, destFile, notificationId, trackTitle, albumId)

            // Verify file integrity — must be > 8KB to be a valid audio file
            if (!destFile.exists() || destFile.length() < 8192) {
                destFile.delete()
                throw Exception("Downloaded file missing or too small (${destFile.length()} bytes)")
            }

            trackDao.updateLocalPath(trackId, destFile.absolutePath)
            downloadArtwork(albumId)

            // Completion notification
            val completeNotification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DOWNLOAD)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Downloaded")
                .setContentText(trackTitle)
                .setAutoCancel(true)
                .setGroup("album_$albumId")
                .build()
            notificationManager.notify(notificationId, completeNotification)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Download failed for $trackId: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                // Clean up partial file
                if (destFile.exists()) destFile.delete()

                // Failure notification
                val failNotification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DOWNLOAD)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Download failed")
                    .setContentText(trackTitle)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(notificationId, failNotification)

                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
            }
        }
    }

    private fun createProgressForegroundInfo(
        notificationId: Int,
        title: String,
        albumId: String,
        progress: Int,
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setGroup("album_$albumId")
            .build()
        return ForegroundInfo(
            notificationId,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun downloadTrack(trackId: String, destFile: File, notificationId: Int, title: String, albumId: String) {
        val streamUrl = urlBuilder.getStreamUrl(trackId)
        val requestBuilder = Request.Builder().url(streamUrl)

        val existingSize = if (destFile.exists()) destFile.length() else 0L
        if (existingSize > 0) {
            requestBuilder.header("Range", "bytes=$existingSize-")
        }

        var response = okHttpClient.newCall(requestBuilder.build()).execute()
        // HTTP 416 = Range not satisfiable — delete partial file and retry without Range
        if (response.code == 416) {
            response.close()
            if (destFile.exists()) destFile.delete()
            val freshRequest = Request.Builder().url(streamUrl).build()
            response = okHttpClient.newCall(freshRequest).execute()
        }
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw Exception("HTTP ${response.code}")
        }

        response.use { resp ->
            val body = resp.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val totalSize = if (resp.code == 206) existingSize + contentLength else contentLength
            val append = resp.code == 206

            FileOutputStream(destFile, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesWritten = if (append) existingSize else 0L
                    var read: Int
                    var lastNotifiedPct = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read

                        if (totalSize > 0) {
                            val pct = ((bytesWritten * 100) / totalSize).toInt()
                            setProgressAsync(workDataOf(KEY_PROGRESS to pct))

                            if (pct / 5 > lastNotifiedPct / 5) {
                                lastNotifiedPct = pct
                                val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DOWNLOAD)
                                    .setSmallIcon(android.R.drawable.stat_sys_download)
                                    .setContentTitle("Downloading")
                                    .setContentText(title)
                                    .setProgress(100, pct, false)
                                    .setOngoing(true)
                                    .setGroup("album_$albumId")
                                    .build()
                                notificationManager.notify(notificationId, notification)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadArtwork(albumId: String) {
        val artworkFile = StoragePaths.artworkFile(applicationContext, albumId)
        if (artworkFile.exists() && artworkFile.length() > 0) return
        artworkFile.parentFile?.mkdirs()

        try {
            val artUrl = urlBuilder.getCoverArtUrl(albumId, 600)
            val request = Request.Builder().url(artUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(artworkFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            response.close()
        } catch (_: Exception) { }
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
        const val KEY_ARTIST_ID = "artist_id"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_SUFFIX = "suffix"
        const val KEY_PROGRESS = "pct"
        const val KEY_ERROR = "error"
    }
}
