package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverPhotoId: String? = null,
    val photoCount: Int = 0,
)
