package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

class CompleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val getNextOccurrenceUseCase: GetNextOccurrenceUseCase,
) {
    suspend operator fun invoke(taskId: String) {
        val task = taskRepository.getTaskDetail(taskId).first() ?: return
        val recurrenceRule = task.recurrenceRule

        if (recurrenceRule != null && task.dueDate != null) {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val nextDate = getNextOccurrenceUseCase(task.dueDate, recurrenceRule)

            if (nextDate != null) {
                taskRepository.rescheduleTask(taskId, nextDate)
            } else {
                taskRepository.completeTask(
                    taskId = taskId,
                    completedAtMillis = Clock.System.now().toEpochMilliseconds(),
                )
            }
        } else {
            taskRepository.completeTask(
                taskId = taskId,
                completedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
}
