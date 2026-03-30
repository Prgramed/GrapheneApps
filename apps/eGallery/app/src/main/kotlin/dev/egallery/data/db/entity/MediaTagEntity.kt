package dev.egallery.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "media_tag",
    primaryKeys = ["nasId", "tagId"],
    indices = [Index("nasId"), Index("tagId")],
)
data class MediaTagEntity(
    val nasId: String,
    val tagId: String,
)
