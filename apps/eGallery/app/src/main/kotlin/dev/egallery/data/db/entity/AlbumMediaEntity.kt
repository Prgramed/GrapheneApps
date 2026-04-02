package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "album_media",
    primaryKeys = ["albumId", "nasId"],
    indices = [Index("nasId")],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["nasId"],
            childColumns = ["nasId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AlbumMediaEntity(
    val albumId: String,
    val nasId: String,
)
