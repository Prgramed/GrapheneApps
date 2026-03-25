package com.grapheneapps.enotes.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("folderId"),
        Index("isPinned"),
        Index("editedAt"),
        Index("deletedAt"),
        Index("syncStatus"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bodyJson: String = "",
    val bodyText: String = "",
    val folderId: String? = null,
    val tags: String = "",
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val encryptedBody: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val remoteEtag: String? = null,
)
