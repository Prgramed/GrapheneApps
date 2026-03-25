package com.prgramed.econtacts.domain.model

data class PhoneNumber(
    val number: String,
    val type: PhoneType = PhoneType.MOBILE,
    val label: String? = null,
)

enum class PhoneType {
    MOBILE, HOME, WORK, OTHER,
}
