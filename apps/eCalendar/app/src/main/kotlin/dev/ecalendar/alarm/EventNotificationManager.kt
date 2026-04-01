package dev.ecalendar.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ecalendar.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val reminderChannel = NotificationChannel(
            CHANNEL_ID,
            "Calendar Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for upcoming calendar events"
        }
        notificationManager.createNotificationChannel(reminderChannel)

        val syncErrorChannel = NotificationChannel(
            SYNC_ERROR_CHANNEL_ID,
            "Sync Issues",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts when calendar sync fails"
        }
        notificationManager.createNotificationChannel(syncErrorChannel)
    }

    fun showReminderNotification(
        uid: String,
        instanceStart: Long,
        title: String,
        location: String?,
        offsetMins: Int,
    ) {
        val timeStr = Instant.ofEpochMilli(instanceStart)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault()))

        val contentText = buildString {
            append(timeStr)
            if (!location.isNullOrBlank()) append(" · $location")
        }

        // Tap → open app
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, uid.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Snooze 10min action
        val snoozeIntent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = EventAlarmReceiver.ACTION_SNOOZE
            putExtra(EventAlarmReceiver.EXTRA_UID, uid)
            putExtra(EventAlarmReceiver.EXTRA_INSTANCE_START, instanceStart)
            putExtra(EventAlarmReceiver.EXTRA_TITLE, title)
            putExtra(EventAlarmReceiver.EXTRA_LOCATION, location)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, "${uid}snooze".hashCode(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notifId = "$uid$instanceStart".hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setWhen(instanceStart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(0, "Snooze 10min", snoozePending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        notificationManager.notify(notifId, notification)
    }

    fun showSyncErrorNotification(eventUid: String, title: String, message: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, "syncerr$eventUid".hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SYNC_ERROR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Sync failed: $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        notificationManager.notify("syncerr$eventUid".hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "ecalendar_reminders"
        const val SYNC_ERROR_CHANNEL_ID = "ecalendar_sync_errors"
    }
}
