package com.prgramed.econtacts.domain.model

data class Contact(
    val id: Long = 0,
    val lookupKey: String = "",
    val displayName: String = "",
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val emails: List<Email> = emptyList(),
    val photoUri: String? = null,
    val starred: Boolean = false,
    val groups: List<ContactGroup> = emptyList(),
    val note: String? = null,
    val uid: String? = null,
    val organization: String? = null,
    val title: String? = null,
    val birthday: String? = null,
    val addresses: List<Address> = emptyList(),
    val websites: List<String> = emptyList(),
)
