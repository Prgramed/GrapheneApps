package com.prgramed.eprayer.domain.model

import kotlin.time.Instant

data class PrayerTime(
    val prayer: Prayer,
    val time: Instant,
    val isNext: Boolean = false,
)
