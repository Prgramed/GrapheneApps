package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(query: String): Flow<List<Task>> =
        taskRepository.searchTasks(query)
}
