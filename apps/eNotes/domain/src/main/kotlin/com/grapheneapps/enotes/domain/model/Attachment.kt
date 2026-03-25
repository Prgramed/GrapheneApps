package com.grapheneapps.enotes.domain.model

data class Attachment(
    val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val localPath: String? = null,
    val remotePath: String? = null,
)
