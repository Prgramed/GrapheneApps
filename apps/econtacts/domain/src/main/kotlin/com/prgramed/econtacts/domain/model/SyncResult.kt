package com.prgramed.econtacts.domain.model

data class SyncResult(
    val added: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val errors: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = errors.isEmpty()
    val total: Int get() = added + updated + deleted
}
