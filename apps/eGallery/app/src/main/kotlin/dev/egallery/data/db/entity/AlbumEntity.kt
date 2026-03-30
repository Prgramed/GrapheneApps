package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverPhotoId: String? = null,
    val photoCount: Int = 0,
    val type: String = "MANUAL", // "MANUAL" or "PEOPLE"
)
