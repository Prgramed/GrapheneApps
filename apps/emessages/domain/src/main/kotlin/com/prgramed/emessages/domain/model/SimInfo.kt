package com.prgramed.emessages.domain.model

data class SimInfo(
    val subscriptionId: Int,
    val displayName: String,
    val slotIndex: Int,
    val carrierName: String,
)
