package com.prgramed.edoist.data.database.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.prgramed.edoist.data.database.entity.ProjectEntity

data class ProjectWithTaskCount(
    @Embedded val project: ProjectEntity,
    @ColumnInfo(name = "task_count") val taskCount: Int,
)
