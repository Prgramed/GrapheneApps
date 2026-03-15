package com.prgramed.eprayer.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.prgramed.eprayer.domain.model.Prayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer Times",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for prayer times"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showPrayerNotification(prayer: Prayer) {
        val displayName = prayer.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Prayer Time")
            .setContentText("It's time for $displayName prayer")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(prayer.ordinal, notification)
    }

    companion object {
        const val CHANNEL_ID = "eprayer_notifications"
    }
}
