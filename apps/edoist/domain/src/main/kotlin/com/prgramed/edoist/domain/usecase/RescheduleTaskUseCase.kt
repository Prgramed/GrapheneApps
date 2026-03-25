package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class RescheduleTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(taskId: String, newDate: LocalDate?) {
        taskRepository.rescheduleTask(taskId, newDate)
    }
}
