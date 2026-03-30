package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "album_media",
    primaryKeys = ["albumId", "nasId"],
    indices = [Index("nasId")],
)
data class AlbumMediaEntity(
    val albumId: String,
    val nasId: String,
)
