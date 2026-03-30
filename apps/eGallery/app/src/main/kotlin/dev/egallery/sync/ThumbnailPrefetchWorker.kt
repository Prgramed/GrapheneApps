package dev.egallery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Thumbnail prefetch is no longer needed — Immich serves thumbnails via
 * /api/assets/{id}/thumbnail which Coil loads directly.
 * Kept as a no-op for WorkManager compatibility.
 */
@HiltWorker
class ThumbnailPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val WORK_NAME = "thumbnail_prefetch"
        const val KEY_PRIORITY_IDS = "priority_ids"
    }
}
