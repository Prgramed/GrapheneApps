package com.prgramed.edoist.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.prgramed.edoist.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for upcoming tasks"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(task: Task, projectName: String?): Notification {
        val completeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_COMPLETE
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() + COMPLETE_REQUEST_OFFSET,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_SNOOZE
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskAlarmReceiver.EXTRA_PROJECT_NAME, projectName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode() + SNOOZE_REQUEST_OFFSET,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = buildString {
            if (!projectName.isNullOrBlank()) append(projectName)
            val dueTime = task.dueTime
            if (dueTime != null) {
                if (isNotEmpty()) append(" \u00B7 ")
                val hour = dueTime.hour
                val minute = dueTime.minute
                val amPm = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                append("$displayHour:${minute.toString().padStart(2, '0')} $amPm")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(task.title)
            .setContentText(contentText.ifEmpty { "Task reminder" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(task.priority.colorArgb.toInt())
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Complete",
                completePendingIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "Snooze 1h",
                snoozePendingIntent,
            )
            .build()
    }

    fun showNotification(task: Task, projectName: String?) {
        val notification = buildNotification(task, projectName)
        notificationManager.notify(task.id.hashCode(), notification)
    }

    fun cancel(taskId: String) {
        notificationManager.cancel(taskId.hashCode())
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        private const val COMPLETE_REQUEST_OFFSET = 100_000
        private const val SNOOZE_REQUEST_OFFSET = 200_000
    }
}
