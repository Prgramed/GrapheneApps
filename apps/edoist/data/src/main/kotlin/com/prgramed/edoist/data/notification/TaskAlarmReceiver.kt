package com.prgramed.edoist.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskNotificationManager: TaskNotificationManager

    @Inject
    lateinit var taskRepository: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: return

        when (intent.action) {
            ACTION_REMINDER -> handleReminder(taskId, taskTitle, intent)
            ACTION_COMPLETE -> handleComplete(taskId)
            ACTION_SNOOZE -> handleSnooze(context, taskId, taskTitle, intent)
        }
    }

    private fun handleReminder(taskId: String, taskTitle: String, intent: Intent) {
        val priorityValue = intent.getIntExtra(EXTRA_TASK_PRIORITY, 4)
        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)

        val task = Task(
            id = taskId,
            title = taskTitle,
            projectId = "",
            priority = Priority.fromValue(priorityValue),
        )
        taskNotificationManager.showNotification(task, projectName)
    }

    private fun handleComplete(taskId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskRepository.completeTask(taskId, System.currentTimeMillis())
                taskNotificationManager.cancel(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(
        context: Context,
        taskId: String,
        taskTitle: String,
        intent: Intent,
    ) {
        // Cancel current notification
        taskNotificationManager.cancel(taskId)

        // Schedule a new alarm 1 hour from now
        val priorityValue = intent.getIntExtra(EXTRA_TASK_PRIORITY, 4)
        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)

        val snoozeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_TASK_PRIORITY, priorityValue)
            putExtra(EXTRA_PROJECT_NAME, projectName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_DURATION_MILLIS,
            pendingIntent,
        )
    }

    companion object {
        const val ACTION_REMINDER = "com.prgramed.edoist.ACTION_TASK_REMINDER"
        const val ACTION_COMPLETE = "com.prgramed.edoist.ACTION_TASK_COMPLETE"
        const val ACTION_SNOOZE = "com.prgramed.edoist.ACTION_TASK_SNOOZE"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_PROJECT_NAME = "extra_project_name"

        private const val SNOOZE_DURATION_MILLIS = 60 * 60 * 1000L // 1 hour
    }
}
