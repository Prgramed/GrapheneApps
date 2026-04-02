package com.prgramed.eprayer.data.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.eprayer.data.widget.PrayerWidgetWorker
import com.prgramed.eprayer.domain.usecase.SchedulePrayerNotificationsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerStartupScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val schedulePrayerNotificationsUseCase: SchedulePrayerNotificationsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleOnStartup() {
        // Schedule today's and tomorrow's prayer alarms
        scope.launch {
            try {
                val javaToday = java.time.LocalDate.now()
                val today = kotlinx.datetime.LocalDate(
                    javaToday.year, javaToday.monthValue, javaToday.dayOfMonth,
                )
                schedulePrayerNotificationsUseCase(today)
                val javaTomorrow = javaToday.plusDays(1)
                val tomorrow = kotlinx.datetime.LocalDate(
                    javaTomorrow.year, javaTomorrow.monthValue, javaTomorrow.dayOfMonth,
                )
                schedulePrayerNotificationsUseCase(tomorrow)
            } catch (_: Exception) { }
        }

        // Ensure periodic widget refresh is scheduled (safety net)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val widgetWork = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(
            2, TimeUnit.HOURS,
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PrayerWidgetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            widgetWork,
        )
    }
}
