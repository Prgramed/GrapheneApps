package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val lrcContent: String? = null,
    val plainText: String? = null,
    val fetchedAt: Long = 0,
)
