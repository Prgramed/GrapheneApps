package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.scheduler.TaskReminderScheduler
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskReminderScheduler: TaskReminderScheduler,
) {
    suspend operator fun invoke(task: Task): String {
        val taskId = taskRepository.createTask(task)

        if (task.dueDate != null && task.dueTime != null) {
            val createdTask = taskRepository.getTaskDetail(taskId).first()
            if (createdTask != null) {
                taskReminderScheduler.scheduleReminder(createdTask)
            }
        }

        return taskId
    }
}
