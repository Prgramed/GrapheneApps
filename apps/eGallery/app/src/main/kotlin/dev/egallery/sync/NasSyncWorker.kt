package dev.egallery.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Triggered after upload to re-sync from Immich and pick up newly uploaded items.
 */
@HiltWorker
class NasSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: NasSyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Timber.d("Post-upload sync: starting full sync to pick up new items")
        syncEngine.startFullSync()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "nas_sync"
    }
}
