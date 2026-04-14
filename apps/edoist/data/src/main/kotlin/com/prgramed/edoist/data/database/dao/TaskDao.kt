package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.prgramed.edoist.data.database.entity.TaskEntity
import com.prgramed.edoist.data.database.entity.TaskLabelCrossRef
import com.prgramed.edoist.data.database.relation.TaskWithLabels
import com.prgramed.edoist.data.database.relation.TaskWithSubtasks
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // ── Inserts ──────────────────────────────────────────────────────────

    @Upsert
    suspend fun insert(task: TaskEntity)

    @Upsert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Upsert
    suspend fun insertTaskLabelCrossRef(crossRef: TaskLabelCrossRef)

    @Upsert
    suspend fun insertTaskLabelCrossRefs(crossRefs: List<TaskLabelCrossRef>)

    @Query("SELECT label_id FROM task_label_cross_ref WHERE task_id = :taskId")
    suspend fun getLabelIdsForTask(taskId: String): List<String>

    // ── Updates ──────────────────────────────────────────────────────────

    @Update
    suspend fun update(task: TaskEntity)

    @Query(
        """
        UPDATE tasks
        SET is_completed = :isCompleted,
            completed_at_millis = :completedAtMillis,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """,
    )
    suspend fun setCompleted(
        taskId: String,
        isCompleted: Boolean,
        completedAtMillis: Long?,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE tasks
        SET due_date_epoch_day = :dueDateEpochDay,
            due_time_minute_of_day = :dueTimeMinuteOfDay,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """,
    )
    suspend fun reschedule(
        taskId: String,
        dueDateEpochDay: Long?,
        dueTimeMinuteOfDay: Int?,
        updatedAtMillis: Long,
    )

    @Query("UPDATE tasks SET sort_order = :sortOrder, updated_at_millis = :updatedAtMillis WHERE id = :taskId")
    suspend fun updateSortOrder(taskId: String, sortOrder: Int, updatedAtMillis: Long)

    @Query(
        """
        UPDATE tasks
        SET section_id = :sectionId,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """,
    )
    suspend fun moveToSection(taskId: String, sectionId: String?, updatedAtMillis: Long)

    @Query(
        """
        UPDATE tasks
        SET project_id = :projectId,
            section_id = NULL,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """,
    )
    suspend fun moveToProject(taskId: String, projectId: String, updatedAtMillis: Long)

    // ── Deletes ──────────────────────────────────────────────────────────

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("DELETE FROM task_label_cross_ref WHERE task_id = :taskId")
    suspend fun deleteLabelsForTask(taskId: String)

    @Query("DELETE FROM tasks WHERE is_completed = 1")
    suspend fun deleteAllCompleted()

    // ── Queries (Flow) ───────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskWithLabels(taskId: String): Flow<TaskWithLabels?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskWithSubtasks(taskId: String): Flow<TaskWithSubtasks?>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Transaction
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN projects p ON t.project_id = p.id
        WHERE p.is_inbox = 1
            AND t.is_completed = 0
            AND t.parent_task_id IS NULL
        ORDER BY t.sort_order ASC
        """,
    )
    fun getInboxTasks(): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN projects p ON t.project_id = p.id
        WHERE p.is_inbox = 1
            AND t.parent_task_id IS NULL
        ORDER BY t.is_completed ASC, t.sort_order ASC
        """,
    )
    fun getInboxTasksAll(): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE due_date_epoch_day <= :todayEpochDay
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY due_date_epoch_day ASC, due_time_minute_of_day ASC, sort_order ASC
        """,
    )
    fun getTodayTasks(todayEpochDay: Long): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE due_date_epoch_day > :todayEpochDay
            AND due_date_epoch_day <= :endEpochDay
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY due_date_epoch_day ASC, due_time_minute_of_day ASC, sort_order ASC
        """,
    )
    fun getUpcomingTasks(todayEpochDay: Long, endEpochDay: Long): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE project_id = :projectId
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun getTasksByProject(projectId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE section_id = :sectionId
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun getTasksBySection(sectionId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE section_id = :sectionId
            AND parent_task_id IS NULL
        ORDER BY is_completed ASC, sort_order ASC
        """,
    )
    fun getTasksBySectionAll(sectionId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE project_id = :projectId
            AND section_id IS NULL
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun getUnsectionedTasksByProject(projectId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE project_id = :projectId
            AND section_id IS NULL
            AND parent_task_id IS NULL
        ORDER BY is_completed ASC, sort_order ASC
        """,
    )
    fun getUnsectionedTasksByProjectAll(projectId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE parent_task_id = :parentId
            AND is_completed = 0
        ORDER BY sort_order ASC
        """,
    )
    fun getSubtasks(parentId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN task_label_cross_ref tlc ON t.id = tlc.task_id
        WHERE tlc.label_id = :labelId
            AND t.is_completed = 0
            AND t.parent_task_id IS NULL
        ORDER BY t.sort_order ASC
        """,
    )
    fun getTasksByLabel(labelId: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
            AND parent_task_id IS NULL
        ORDER BY is_completed ASC, updated_at_millis DESC
        """,
    )
    fun searchTasks(query: String): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE project_id = :projectId
            AND due_date_epoch_day >= :startEpochDay
            AND due_date_epoch_day <= :endEpochDay
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY due_date_epoch_day ASC, due_time_minute_of_day ASC, sort_order ASC
        """,
    )
    fun getTasksByProjectAndDateRange(
        projectId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE priority = :priority
            AND is_completed = 0
            AND parent_task_id IS NULL
        ORDER BY due_date_epoch_day ASC, sort_order ASC
        """,
    )
    fun getTasksByPriority(priority: Int): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE project_id = :projectId
            AND is_completed = 1
        ORDER BY completed_at_millis DESC
        LIMIT :limit
        """,
    )
    fun getCompletedTasks(projectId: String, limit: Int = 50): Flow<List<TaskWithLabels>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 1
        ORDER BY completed_at_millis DESC
        LIMIT :limit
        """,
    )
    fun getAllCompletedTasks(limit: Int = 50): Flow<List<TaskWithLabels>>

    // ── Queries (suspend) ────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
            AND due_date_epoch_day IS NOT NULL
            AND due_time_minute_of_day IS NOT NULL
        """,
    )
    suspend fun getTasksWithReminders(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE updated_at_millis >= :sinceMillis")
    suspend fun getTasksModifiedSince(sinceMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE project_id = :projectId
            AND is_completed = 0
            AND parent_task_id IS NULL
        """,
    )
    fun getActiveTaskCount(projectId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE due_date_epoch_day <= :todayEpochDay
            AND is_completed = 0
            AND parent_task_id IS NULL
        """,
    )
    fun getTodayTaskCount(todayEpochDay: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM tasks WHERE project_id = :projectId")
    suspend fun getMaxSortOrder(projectId: String): Int
}
