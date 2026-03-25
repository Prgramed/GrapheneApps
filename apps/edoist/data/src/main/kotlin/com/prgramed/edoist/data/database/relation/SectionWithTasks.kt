package com.prgramed.edoist.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.prgramed.edoist.data.database.entity.SectionEntity
import com.prgramed.edoist.data.database.entity.TaskEntity

data class SectionWithTasks(
    @Embedded val section: SectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "section_id",
    )
    val tasks: List<TaskEntity>,
)
