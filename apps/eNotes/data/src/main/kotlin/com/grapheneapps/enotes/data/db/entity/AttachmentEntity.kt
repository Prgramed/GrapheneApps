package com.grapheneapps.enotes.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val localPath: String? = null,
    val remotePath: String? = null,
)
