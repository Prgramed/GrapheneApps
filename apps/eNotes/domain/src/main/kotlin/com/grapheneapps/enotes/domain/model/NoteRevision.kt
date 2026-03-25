package com.grapheneapps.enotes.domain.model

data class NoteRevision(
    val id: String,
    val noteId: String,
    val bodySnapshot: String,
    val encryptedSnapshot: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deltaChars: Int = 0,
)
