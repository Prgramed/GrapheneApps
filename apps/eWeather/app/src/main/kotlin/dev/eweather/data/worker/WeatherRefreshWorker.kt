package dev.eweather.data.worker

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
import dev.eweather.data.db.dao.LocationDao
import dev.eweather.data.db.entity.toDomain
import dev.eweather.domain.repository.AlertRepository
import dev.eweather.domain.repository.WeatherRepository
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.eweather.ui.widget.WeatherWidget
import dev.eweather.ui.widget.WeatherWidgetReceiver
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class WeatherRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationDao: LocationDao,
    private val weatherRepository: WeatherRepository,
    private val alertRepository: AlertRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_RETRIES) return Result.failure()

        return try {
            val locations = locationDao.getAll().map { it.toDomain() }

            for (location in locations) {
                try {
                    weatherRepository.refreshWeather(location)
                } catch (e: Exception) {
                    Timber.w(e, "Background refresh failed for ${location.name}")
                }
                try {
                    alertRepository.refreshAlerts(location)
                } catch (e: Exception) {
                    Timber.w(e, "Alert refresh failed for ${location.name}")
                }
            }

            // Update widget with fresh data
            try {
                val manager = GlanceAppWidgetManager(applicationContext)
                val ids = manager.getGlanceIds(WeatherWidget::class.java)
                ids.forEach { WeatherWidget().update(applicationContext, it) }
            } catch (_: Exception) { }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "WeatherRefreshWorker failed")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "eweather_refresh"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context, intervalHours: Int = 1) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
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
