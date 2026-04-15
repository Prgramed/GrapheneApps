package dev.ecalendar.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 5) return Result.failure()

        return try {
            syncCoordinator.syncAll()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ecalendar_sync"

        /**
         * Schedules the periodic sync. Interval is clamped to 15 minutes (WorkManager's
         * minimum periodic interval). Pass 0 or negative to cancel.
         */
        fun schedule(context: Context, intervalMinutes: Int = 60) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val clamped = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                clamped.toLong(), TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
