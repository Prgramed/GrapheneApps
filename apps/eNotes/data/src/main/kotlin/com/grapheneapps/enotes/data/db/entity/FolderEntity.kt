package com.grapheneapps.enotes.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index("parentId"),
        Index("name"),
    ],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val iconEmoji: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "LOCAL_ONLY",
)
