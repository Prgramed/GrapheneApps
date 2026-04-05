package dev.egallery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic worker that scans for new device photos and queues them for upload.
 * Independent of sync — runs on its own schedule (every 30 min when auto-upload is ON).
 */
@HiltWorker
class UploadScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: NasSyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val queued = syncEngine.scanAndQueueUploads()
            Timber.d("UploadScanWorker: $queued new photos queued")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "UploadScanWorker failed")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "upload_scan"
        const val PERIODIC_WORK_NAME = "upload_scan_periodic"
    }
}
