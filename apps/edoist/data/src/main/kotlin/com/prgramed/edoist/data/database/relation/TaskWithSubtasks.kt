package com.prgramed.edoist.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.prgramed.edoist.data.database.entity.TaskEntity

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "parent_task_id",
    )
    val subtasks: List<TaskEntity>,
)
