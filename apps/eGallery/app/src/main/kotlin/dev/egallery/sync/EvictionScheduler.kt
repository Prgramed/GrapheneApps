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
        // Unmetered + charging to avoid burning mobile data / battery when the worker
        // auto-downloads NAS-only photos from the last-year window. Eviction alone
        // doesn't need network, but the combined worker does.
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val request = PeriodicWorkRequestBuilder<EvictionWorker>(
            1, TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EvictionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
        Timber.d("Scheduled daily cache maintenance worker (evict + auto-download)")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(EvictionWorker.WORK_NAME)
    }
}
