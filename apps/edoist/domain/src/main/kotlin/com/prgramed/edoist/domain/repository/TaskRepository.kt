package com.prgramed.edoist.domain.repository

import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TaskRepository {

    fun getTodayTasks(today: LocalDate): Flow<List<Task>>

    fun getUpcomingTasks(startDate: LocalDate, days: Int): Flow<List<Task>>

    fun getInboxTasks(): Flow<List<Task>>

    fun getTasksByProject(projectId: String): Flow<List<Task>>

    fun getTasksBySection(sectionId: String): Flow<List<Task>>

    fun getUnsectionedTasksByProject(projectId: String): Flow<List<Task>>

    fun getTasksByLabel(labelId: String): Flow<List<Task>>

    fun getTasksByProjectAndDateRange(
        projectId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Task>>

    fun getTasksByPriority(priority: Priority): Flow<List<Task>>

    fun getSubtasks(parentTaskId: String): Flow<List<Task>>

    fun getTaskDetail(taskId: String): Flow<Task?>

    fun searchTasks(query: String): Flow<List<Task>>

    fun getCompletedTasks(projectId: String? = null): Flow<List<Task>>

    fun getTodayTaskCount(today: LocalDate): Flow<Int>

    fun getActiveTaskCount(): Flow<Int>

    fun getTasksWithReminders(): Flow<List<Task>>

    suspend fun createTask(task: Task): String

    suspend fun updateTask(task: Task)

    suspend fun completeTask(taskId: String, completedAtMillis: Long)

    suspend fun uncompleteTask(taskId: String)

    suspend fun rescheduleTask(taskId: String, newDate: LocalDate?)

    suspend fun moveTaskToProject(taskId: String, projectId: String)

    suspend fun moveTaskToSection(taskId: String, sectionId: String?)

    suspend fun deleteTask(taskId: String)

    suspend fun updateTaskLabels(taskId: String, labelIds: List<String>)

    suspend fun updateSortOrder(taskId: String, sortOrder: Int)
}
