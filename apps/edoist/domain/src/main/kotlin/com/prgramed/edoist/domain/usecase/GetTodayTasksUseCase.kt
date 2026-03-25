package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.TaskGroup
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

class GetTodayTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): Flow<List<TaskGroup>> =
        taskRepository.getTodayTasks(today).map { tasks ->
            val (overdue, todayTasks) = tasks.partition { task ->
                task.dueDate != null && task.dueDate < today
            }

            buildList {
                if (overdue.isNotEmpty()) {
                    add(TaskGroup.Overdue(tasks = overdue))
                }
                if (todayTasks.isNotEmpty()) {
                    add(TaskGroup.ByDate(date = today, tasks = todayTasks))
                }
            }
        }
}
