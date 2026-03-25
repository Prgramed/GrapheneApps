package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index("sort_order")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "is_inbox") val isInbox: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "default_view") val defaultView: String = "LIST",
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
