package com.prgramed.econtacts.domain.model

data class Address(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val type: AddressType = AddressType.HOME,
)

enum class AddressType {
    HOME, WORK, OTHER,
}
