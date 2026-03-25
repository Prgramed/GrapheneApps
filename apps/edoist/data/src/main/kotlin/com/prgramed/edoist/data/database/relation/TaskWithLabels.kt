package com.prgramed.edoist.data.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.prgramed.edoist.data.database.entity.LabelEntity
import com.prgramed.edoist.data.database.entity.TaskEntity
import com.prgramed.edoist.data.database.entity.TaskLabelCrossRef

data class TaskWithLabels(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskLabelCrossRef::class,
            parentColumn = "task_id",
            entityColumn = "label_id",
        ),
    )
    val labels: List<LabelEntity>,
)
