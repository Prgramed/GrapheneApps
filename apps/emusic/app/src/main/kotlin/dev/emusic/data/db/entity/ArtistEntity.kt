package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index("name"),
    ],
)
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
    val starred: Boolean = false,
)
