package com.prgramed.econtacts.domain.model

data class SpeedDial(
    val key: Int,
    val contactId: Long,
    val displayName: String,
    val phoneNumber: String,
)
