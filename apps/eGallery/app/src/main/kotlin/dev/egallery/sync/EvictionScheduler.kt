package dev.egallery.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvictionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleDaily() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<EvictionWorker>(
            1, TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EvictionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.d("Scheduled daily eviction worker")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(EvictionWorker.WORK_NAME)
    }
}
