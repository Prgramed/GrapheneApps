package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInboxTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = taskRepository.getInboxTasks()
}
