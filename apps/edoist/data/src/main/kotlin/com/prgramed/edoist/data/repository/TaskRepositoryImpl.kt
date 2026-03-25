package com.prgramed.edoist.data.repository

import com.prgramed.edoist.data.database.dao.LabelDao
import com.prgramed.edoist.data.database.dao.TaskDao
import com.prgramed.edoist.data.database.entity.LabelEntity
import com.prgramed.edoist.data.database.entity.TaskEntity
import com.prgramed.edoist.data.database.entity.TaskLabelCrossRef
import com.prgramed.edoist.data.database.relation.TaskWithLabels
import com.prgramed.edoist.domain.model.Label
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val labelDao: LabelDao,
) : TaskRepository {

    // ── Mapping ────────────────────────────────────────────────────────────

    private fun TaskWithLabels.toDomain(): Task = Task(
        id = task.id,
        title = task.title,
        description = task.description,
        projectId = task.projectId,
        sectionId = task.sectionId,
        parentTaskId = task.parentTaskId,
        priority = Priority.fromValue(task.priority),
        dueDate = task.dueDateEpochDay?.let { LocalDate.fromEpochDays(it.toInt()) },
        dueTime = task.dueTimeMinuteOfDay?.let { LocalTime(it / 60, it % 60) },
        dueTimezone = task.dueTimezone,
        recurrenceRule = task.recurrenceRule?.let { RecurrenceRule.fromRRuleString(it) },
        isCompleted = task.isCompleted,
        completedAtMillis = task.completedAtMillis,
        labels = labels.map { it.toDomain() },
        sortOrder = task.sortOrder,
        createdAtMillis = task.createdAtMillis,
        updatedAtMillis = task.updatedAtMillis,
    )

    private fun LabelEntity.toDomain(): Label = Label(
        id = id,
        name = name,
        color = color,
        sortOrder = sortOrder,
    )

    private fun Task.toEntity(): TaskEntity = TaskEntity(
        id = id,
        title = title,
        description = description,
        projectId = projectId,
        sectionId = sectionId,
        parentTaskId = parentTaskId,
        priority = priority.value,
        dueDateEpochDay = dueDate?.toEpochDays()?.toLong(),
        dueTimeMinuteOfDay = dueTime?.let { it.hour * 60 + it.minute },
        dueTimezone = dueTimezone,
        recurrenceRule = recurrenceRule?.toRRuleString(),
        isCompleted = isCompleted,
        completedAtMillis = completedAtMillis,
        sortOrder = sortOrder,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun Flow<List<TaskWithLabels>>.mapToDomain(): Flow<List<Task>> =
        map { list -> list.map { it.toDomain() } }

    // ── Flow queries ───────────────────────────────────────────────────────

    override fun getTodayTasks(today: LocalDate): Flow<List<Task>> =
        taskDao.getTodayTasks(today.toEpochDays().toLong()).mapToDomain()

    override fun getUpcomingTasks(startDate: LocalDate, days: Int): Flow<List<Task>> {
        val endDate = startDate.toEpochDays().toLong() + days
        return taskDao.getUpcomingTasks(
            startDate.toEpochDays().toLong(),
            endDate,
        ).mapToDomain()
    }

    override fun getInboxTasks(): Flow<List<Task>> =
        taskDao.getInboxTasks().mapToDomain()

    override fun getTasksByProject(projectId: String): Flow<List<Task>> =
        taskDao.getTasksByProject(projectId).mapToDomain()

    override fun getTasksBySection(sectionId: String): Flow<List<Task>> =
        taskDao.getTasksBySection(sectionId).mapToDomain()

    override fun getUnsectionedTasksByProject(projectId: String): Flow<List<Task>> =
        taskDao.getUnsectionedTasksByProject(projectId).mapToDomain()

    override fun getTasksByLabel(labelId: String): Flow<List<Task>> =
        taskDao.getTasksByLabel(labelId).mapToDomain()

    override fun getTasksByProjectAndDateRange(
        projectId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Task>> = taskDao.getTasksByProjectAndDateRange(
        projectId,
        startDate.toEpochDays().toLong(),
        endDate.toEpochDays().toLong(),
    ).mapToDomain()

    override fun getTasksByPriority(priority: Priority): Flow<List<Task>> =
        taskDao.getTasksByPriority(priority.value).mapToDomain()

    override fun getSubtasks(parentTaskId: String): Flow<List<Task>> =
        taskDao.getSubtasks(parentTaskId).mapToDomain()

    override fun getTaskDetail(taskId: String): Flow<Task?> =
        combine(
            taskDao.getTaskWithLabels(taskId),
            taskDao.getSubtasks(taskId),
        ) { taskWithLabels, subtasksList ->
            taskWithLabels?.toDomain()?.copy(
                subtasks = subtasksList.map { it.toDomain() },
            )
        }

    override fun searchTasks(query: String): Flow<List<Task>> =
        taskDao.searchTasks(query).mapToDomain()

    override fun getCompletedTasks(projectId: String?): Flow<List<Task>> {
        val pid = projectId ?: return taskDao.getCompletedTasks("").mapToDomain()
        return taskDao.getCompletedTasks(pid).mapToDomain()
    }

    override fun getTodayTaskCount(today: LocalDate): Flow<Int> =
        taskDao.getTodayTaskCount(today.toEpochDays().toLong())

    override fun getActiveTaskCount(): Flow<Int> =
        taskDao.getTodayTaskCount(Long.MAX_VALUE)

    override fun getTasksWithReminders(): Flow<List<Task>> {
        // TaskDao.getTasksWithReminders() is a suspend function returning List<TaskEntity>,
        // so we wrap it by querying all uncompleted tasks with due dates via a flow-based query.
        // We use today tasks with a far-future date to capture all tasks with reminders.
        return taskDao.getTodayTasks(Long.MAX_VALUE).mapToDomain().map { tasks ->
            tasks.filter { it.dueDate != null && it.dueTime != null }
        }
    }

    // ── Mutations ──────────────────────────────────────────────────────────

    override suspend fun createTask(task: Task): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = task.copy(
            id = id,
            createdAtMillis = now,
            updatedAtMillis = now,
        ).toEntity()
        taskDao.insert(entity)
        if (task.labels.isNotEmpty()) {
            val crossRefs = task.labels.map { label ->
                TaskLabelCrossRef(taskId = id, labelId = label.id)
            }
            taskDao.insertTaskLabelCrossRefs(crossRefs)
        }
        return id
    }

    override suspend fun updateTask(task: Task) {
        val now = System.currentTimeMillis()
        val entity = task.copy(updatedAtMillis = now).toEntity()
        taskDao.update(entity)
        taskDao.deleteLabelsForTask(task.id)
        if (task.labels.isNotEmpty()) {
            val crossRefs = task.labels.map { label ->
                TaskLabelCrossRef(taskId = task.id, labelId = label.id)
            }
            taskDao.insertTaskLabelCrossRefs(crossRefs)
        }
    }

    override suspend fun completeTask(taskId: String, completedAtMillis: Long) {
        taskDao.setCompleted(
            taskId = taskId,
            isCompleted = true,
            completedAtMillis = completedAtMillis,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun uncompleteTask(taskId: String) {
        taskDao.setCompleted(
            taskId = taskId,
            isCompleted = false,
            completedAtMillis = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun rescheduleTask(taskId: String, newDate: LocalDate?) {
        taskDao.reschedule(
            taskId = taskId,
            dueDateEpochDay = newDate?.toEpochDays()?.toLong(),
            dueTimeMinuteOfDay = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun moveTaskToProject(taskId: String, projectId: String) {
        taskDao.moveToProject(taskId, projectId, System.currentTimeMillis())
    }

    override suspend fun moveTaskToSection(taskId: String, sectionId: String?) {
        taskDao.moveToSection(taskId, sectionId, System.currentTimeMillis())
    }

    override suspend fun deleteTask(taskId: String) {
        taskDao.deleteById(taskId)
    }

    override suspend fun updateTaskLabels(taskId: String, labelIds: List<String>) {
        taskDao.deleteLabelsForTask(taskId)
        if (labelIds.isNotEmpty()) {
            val crossRefs = labelIds.map { labelId ->
                TaskLabelCrossRef(taskId = taskId, labelId = labelId)
            }
            taskDao.insertTaskLabelCrossRefs(crossRefs)
        }
    }

    override suspend fun updateSortOrder(taskId: String, sortOrder: Int) {
        taskDao.updateSortOrder(taskId, sortOrder, System.currentTimeMillis())
    }
}
