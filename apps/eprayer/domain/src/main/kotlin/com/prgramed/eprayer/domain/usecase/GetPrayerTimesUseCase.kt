package com.prgramed.eprayer.domain.usecase

import com.prgramed.eprayer.domain.model.PrayerDay
import com.prgramed.eprayer.domain.repository.PrayerTimesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class GetPrayerTimesUseCase @Inject constructor(
    private val prayerTimesRepository: PrayerTimesRepository,
) {
    operator fun invoke(date: LocalDate): Flow<PrayerDay> =
        prayerTimesRepository.getPrayerTimes(date)
}
