package dev.egallery.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    fun schedulePeriodic(intervalHours: Int = 6) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<NasSyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            NasSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.d("Scheduled periodic sync every ${intervalHours}h")
    }

    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<NasSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(request)
        Timber.d("Enqueued one-time sync")
    }

    fun cancel() {
        workManager.cancelUniqueWork(NasSyncWorker.WORK_NAME)
        Timber.d("Cancelled periodic sync")
    }
}
