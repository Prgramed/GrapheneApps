package com.prgramed.econtacts.domain.model

data class Email(
    val address: String,
    val type: EmailType = EmailType.HOME,
    val label: String? = null,
)

enum class EmailType {
    HOME, WORK, OTHER,
}
