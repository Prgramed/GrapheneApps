package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "icon_name") val iconName: String,
    val color: Long,
    val query: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
