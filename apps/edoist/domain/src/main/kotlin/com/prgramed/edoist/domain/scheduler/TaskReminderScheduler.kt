package com.prgramed.edoist.domain.scheduler

import com.prgramed.edoist.domain.model.Task

interface TaskReminderScheduler {

    fun scheduleReminder(task: Task)

    fun cancelReminder(taskId: String)

    fun rescheduleAllReminders(tasks: List<Task>)

    fun cancelAllReminders()
}
