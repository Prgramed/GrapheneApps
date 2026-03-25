package com.prgramed.edoist.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.entity.SectionEntity

data class ProjectWithSections(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "project_id",
    )
    val sections: List<SectionEntity>,
)
