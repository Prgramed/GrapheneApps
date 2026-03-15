package com.prgramed.eprayer.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.eprayer.data.widget.PrayerWidgetWorker
import java.util.concurrent.TimeUnit

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWorker(context)
    }

    private fun scheduleWorker(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(
            30, TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PrayerWidgetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
