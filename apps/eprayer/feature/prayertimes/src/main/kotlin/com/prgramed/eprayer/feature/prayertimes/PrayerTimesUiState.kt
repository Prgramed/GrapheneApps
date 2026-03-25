package com.prgramed.eprayer.feature.prayertimes

import com.prgramed.eprayer.domain.model.PrayerDay
import com.prgramed.eprayer.domain.model.PrayerTime
import kotlin.time.Duration

data class PrayerTimesUiState(
    val prayerDay: PrayerDay? = null,
    val nextPrayer: PrayerTime? = null,
    val timeRemaining: Duration? = null,
    val cityName: String? = null,
    val hijriDate: String? = null,
    val enabledNotifications: Set<String> = setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA"),
    val isLoading: Boolean = true,
    val error: String? = null,
)
