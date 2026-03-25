package com.prgramed.econtacts.domain.model

data class CardDavAccount(
    val serverUrl: String,
    val username: String,
    val addressBookPath: String = "",
)
