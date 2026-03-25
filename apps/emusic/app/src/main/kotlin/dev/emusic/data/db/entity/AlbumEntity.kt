package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("artistId"),
        Index("starred"),
        Index("year"),
        Index("name"),
        Index("genre"),
    ],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArtId: String? = null,
    val trackCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: Boolean = false,
    val playCount: Int = 0,
)
