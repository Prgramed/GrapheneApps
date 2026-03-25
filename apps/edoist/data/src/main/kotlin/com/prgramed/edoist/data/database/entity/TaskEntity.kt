package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["section_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("project_id"),
        Index("section_id"),
        Index("parent_task_id"),
        Index("due_date_epoch_day"),
        Index("is_completed"),
        Index("priority"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "section_id") val sectionId: String? = null,
    @ColumnInfo(name = "parent_task_id") val parentTaskId: String? = null,
    val priority: Int = 4,
    @ColumnInfo(name = "due_date_epoch_day") val dueDateEpochDay: Long? = null,
    @ColumnInfo(name = "due_time_minute_of_day") val dueTimeMinuteOfDay: Int? = null,
    @ColumnInfo(name = "due_timezone") val dueTimezone: String? = null,
    @ColumnInfo(name = "recurrence_rule") val recurrenceRule: String? = null,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at_millis") val completedAtMillis: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
