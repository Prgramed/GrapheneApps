package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackCount: Int = 0,
    val duration: Int = 0,
    val coverArtId: String? = null,
    val public: Boolean = false,
    val comment: String? = null,
    val createdAt: String? = null,
    val changedAt: String? = null,
    val pinned: Boolean = false,
)
