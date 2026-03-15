package com.prgramed.eprayer.domain.usecase

import com.prgramed.eprayer.domain.repository.PrayerTimesRepository
import com.prgramed.eprayer.domain.scheduler.PrayerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class SchedulePrayerNotificationsUseCase @Inject constructor(
    private val prayerTimesRepository: PrayerTimesRepository,
    private val prayerScheduler: PrayerScheduler,
) {
    suspend operator fun invoke(date: LocalDate) {
        val prayerDay = prayerTimesRepository.getPrayerTimes(date).first()
        prayerScheduler.scheduleAlarms(prayerDay)
    }
}
