package com.prgramed.eprayer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prgramed.eprayer.data.widget.PrayerWidgetWorker
import java.util.concurrent.TimeUnit

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Refresh data every time the widget updates
        val request = OneTimeWorkRequestBuilder<PrayerWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Schedule periodic refresh
        val workRequest = PeriodicWorkRequestBuilder<PrayerWidgetWorker>(
            6, TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PrayerWidgetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
