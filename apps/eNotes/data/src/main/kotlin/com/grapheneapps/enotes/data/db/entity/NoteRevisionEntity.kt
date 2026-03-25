package com.grapheneapps.enotes.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_revisions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("noteId"),
        Index("createdAt"),
    ],
)
data class NoteRevisionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val bodySnapshot: String,
    val encryptedSnapshot: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deltaChars: Int = 0,
)
