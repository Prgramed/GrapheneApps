package dev.emusic.data.download

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.domain.model.DownloadState
import dev.emusic.domain.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    fun hasEnoughStorage(): Boolean {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBytes > MIN_FREE_BYTES
    }

    suspend fun enqueue(track: Track): Boolean {
        if (!hasEnoughStorage()) return false

        // Check if already downloaded on disk
        if (track.localPath != null) return false

        // Prune any stale completed/failed work for this track so KEEP doesn't block
        workManager.pruneWork()

        // Always use CONNECTED — VPN (Tailscale) may report as metered even on WiFi
        val networkType = NetworkType.CONNECTED
        val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TRACK_ID to track.id,
                    DownloadWorker.KEY_ARTIST_ID to track.artistId,
                    DownloadWorker.KEY_ALBUM_ID to track.albumId,
                    DownloadWorker.KEY_SUFFIX to (track.suffix ?: "mp3"),
                ),
            )
            .setConstraints(constraints)
            .addTag("download")
            .addTag(track.id)
            .addTag("album_${track.albumId}")
            .build()

        workManager.enqueueUniqueWork(
            "download_${track.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
        return true
    }

    suspend fun enqueueAlbum(tracks: List<Track>) {
        tracks.forEach { enqueue(it) }
    }

    fun cancel(trackId: String) {
        workManager.cancelUniqueWork("download_$trackId")
    }

    fun cancelAlbum(albumId: String) {
        workManager.cancelAllWorkByTag("album_$albumId")
    }

    fun observeDownloadState(trackId: String): Flow<DownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow("download_$trackId")
            .map { infos -> infos.firstOrNull().toDownloadState(trackId) }

    fun observeAll(): Flow<List<Pair<String, DownloadState>>> =
        workManager.getWorkInfosByTagFlow("download")
            .map { infos ->
                infos.map { info ->
                    val id = info.tags.firstOrNull { it != "download" && !it.startsWith("album_") } ?: ""
                    id to info.toDownloadState(id)
                }
            }

    private fun WorkInfo?.toDownloadState(trackId: String): DownloadState {
        if (this == null) return DownloadState.NotDownloaded
        return when (state) {
            WorkInfo.State.ENQUEUED -> DownloadState.Queued(trackId)
            WorkInfo.State.RUNNING -> {
                val pct = progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                DownloadState.Downloading(trackId, pct)
            }
            WorkInfo.State.SUCCEEDED -> DownloadState.Downloaded(trackId, trackId)
            WorkInfo.State.FAILED -> {
                val error = outputData.getString(DownloadWorker.KEY_ERROR) ?: "Download failed"
                DownloadState.Failed(trackId, error)
            }
            WorkInfo.State.CANCELLED -> DownloadState.NotDownloaded
            WorkInfo.State.BLOCKED -> DownloadState.Queued(trackId)
        }
    }

    companion object {
        private const val MIN_FREE_BYTES = 200L * 1024 * 1024 // 200 MB
    }
}
