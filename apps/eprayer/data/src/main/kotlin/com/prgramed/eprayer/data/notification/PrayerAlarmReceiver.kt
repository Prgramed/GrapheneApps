package com.prgramed.eprayer.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.eprayer.data.widget.PrayerWidgetWorker
import com.prgramed.eprayer.domain.model.Prayer
import com.prgramed.eprayer.domain.usecase.SchedulePrayerNotificationsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: PrayerNotificationManager

    @Inject
    lateinit var schedulePrayerNotificationsUseCase: SchedulePrayerNotificationsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
        notificationManager.showPrayerNotification(prayer)

        // Refresh widget to show the next prayer
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<PrayerWidgetWorker>().build())

        // If this was the last prayer of the day (Isha), reschedule for tomorrow
        if (prayer == Prayer.ISHA) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val javaTomorrow = java.time.LocalDate.now().plusDays(1)
                    val tomorrow = kotlinx.datetime.LocalDate(
                        javaTomorrow.year, javaTomorrow.monthValue, javaTomorrow.dayOfMonth,
                    )
                    schedulePrayerNotificationsUseCase(tomorrow)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    }
}
