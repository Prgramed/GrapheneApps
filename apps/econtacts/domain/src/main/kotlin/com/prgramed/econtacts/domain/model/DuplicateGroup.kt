package com.prgramed.econtacts.domain.model

data class DuplicateGroup(
    val contacts: List<Contact>,
    val matchReason: String,
)
