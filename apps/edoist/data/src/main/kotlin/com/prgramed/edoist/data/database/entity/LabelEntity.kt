package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
