package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scrobbles",
    indices = [
        Index("trackId"),
        Index("timestamp"),
    ],
)
data class ScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val timestamp: Long,
    val durationMs: Long,
)
