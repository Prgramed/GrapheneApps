package com.prgramed.eprayer.domain.usecase

import com.prgramed.eprayer.domain.model.PrayerTime
import com.prgramed.eprayer.domain.repository.PrayerTimesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNextPrayerUseCase @Inject constructor(
    private val prayerTimesRepository: PrayerTimesRepository,
) {
    operator fun invoke(): Flow<PrayerTime?> =
        prayerTimesRepository.getNextPrayer()
}
