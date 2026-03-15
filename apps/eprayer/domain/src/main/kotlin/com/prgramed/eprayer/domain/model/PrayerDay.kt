package com.prgramed.eprayer.domain.model

import kotlinx.datetime.LocalDate

data class PrayerDay(
    val date: LocalDate,
    val times: List<PrayerTime>,
    val nextPrayer: PrayerTime?,
)
