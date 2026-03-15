package com.prgramed.eprayer.domain.repository

import com.prgramed.eprayer.domain.model.PrayerDay
import com.prgramed.eprayer.domain.model.PrayerTime
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PrayerTimesRepository {
    fun getPrayerTimes(date: LocalDate): Flow<PrayerDay>
    fun getNextPrayer(): Flow<PrayerTime?>
}
