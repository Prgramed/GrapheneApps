package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.scheduler.TaskReminderScheduler
import javax.inject.Inject

class UpdateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskReminderScheduler: TaskReminderScheduler,
) {
    suspend operator fun invoke(task: Task) {
        taskRepository.updateTask(task)

        if (task.dueDate != null && task.dueTime != null) {
            taskReminderScheduler.scheduleReminder(task)
        } else {
            taskReminderScheduler.cancelReminder(task.id)
        }
    }
}
