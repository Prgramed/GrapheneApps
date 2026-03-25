package com.grapheneapps.enotes.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Entity(tableName = "notes_fts")
@Fts4(
    contentEntity = NoteEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
data class NoteFtsEntity(
    val title: String,
    val bodyText: String,
)
