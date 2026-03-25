package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id")],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_collapsed") val isCollapsed: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)
