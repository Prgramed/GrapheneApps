package com.prgramed.edoist.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.scheduler.TaskReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var taskReminderScheduler: TaskReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasksWithReminders = taskRepository.getTasksWithReminders().first()
                taskReminderScheduler.rescheduleAllReminders(tasksWithReminders)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
