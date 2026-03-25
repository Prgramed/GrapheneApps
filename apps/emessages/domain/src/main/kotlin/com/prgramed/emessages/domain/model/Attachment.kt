package com.prgramed.emessages.domain.model

data class Attachment(
    val uri: String,
    val mimeType: String,
    val fileName: String? = null,
)
