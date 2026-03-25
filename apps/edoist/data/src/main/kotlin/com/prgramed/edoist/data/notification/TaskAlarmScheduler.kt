package com.prgramed.edoist.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.scheduler.TaskReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TaskReminderScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(task: Task) {
        val dueDate = task.dueDate ?: return
        val dueTime = task.dueTime ?: return

        val alarmTimeMillis = computeAlarmTimeMillis(dueDate, dueTime)
        if (alarmTimeMillis <= System.currentTimeMillis()) return

        val intent = createAlarmIntent(task)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent,
            )
        } catch (_: SecurityException) {
            // SCHEDULE_EXACT_ALARM not granted — fall back to inexact
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent,
            )
        }
    }

    override fun cancelReminder(taskId: String) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun rescheduleAllReminders(tasks: List<Task>) {
        cancelAllReminders()
        tasks.forEach { scheduleReminder(it) }
    }

    override fun cancelAllReminders() {
        // AlarmManager doesn't provide a "cancel all" API, so callers should
        // track task IDs and cancel individually.  This implementation is a
        // best-effort no-op since we don't persist scheduled alarm IDs.
    }

    private fun computeAlarmTimeMillis(date: LocalDate, time: LocalTime): Long {
        val tz = TimeZone.currentSystemDefault()
        val dateTime = date.atTime(time)
        return dateTime.toInstant(tz).toEpochMilliseconds()
    }

    private fun createAlarmIntent(task: Task): Intent =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_REMINDER
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_PRIORITY, task.priority.value)
        }
}
