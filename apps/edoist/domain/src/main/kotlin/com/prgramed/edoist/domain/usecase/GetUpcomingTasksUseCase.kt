package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.TaskGroup
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject

class GetUpcomingTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        days: Int = 7,
    ): Flow<List<TaskGroup.ByDate>> =
        taskRepository.getUpcomingTasks(today, days).map { tasks ->
            val tasksByDate = tasks.groupBy { it.dueDate }

            (0 until days).mapNotNull { offset ->
                val date = today.plus(offset, DateTimeUnit.DAY)
                val dateTasks = tasksByDate[date]
                if (dateTasks != null && dateTasks.isNotEmpty()) {
                    TaskGroup.ByDate(date = date, tasks = dateTasks)
                } else {
                    null
                }
            }
        }
}
