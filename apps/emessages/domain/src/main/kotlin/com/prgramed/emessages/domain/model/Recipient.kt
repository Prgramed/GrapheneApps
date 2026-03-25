package com.prgramed.emessages.domain.model

data class Recipient(
    val address: String,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val contactId: Long? = null,
)
