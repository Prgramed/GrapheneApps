package com.prgramed.eprayer.data.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.domain.model.Prayer
import com.prgramed.eprayer.domain.model.PrayerDay
import com.prgramed.eprayer.domain.model.PrayerTime
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.PrayerTimesRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class PrayerTimesRepositoryImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : PrayerTimesRepository {

    override fun getPrayerTimes(date: LocalDate): Flow<PrayerDay> =
        combine(
            locationRepository.getCurrentLocation(),
            userPreferencesRepository.getUserPreferences(),
        ) { location, prefs ->
            val coordinates = Coordinates(location.latitude, location.longitude)
            val params = mapCalculationMethod(prefs.calculationMethod).parameters.copy(
                madhab = mapMadhab(prefs.madhab),
            )
            @Suppress("DEPRECATION")
            val dateComponents = DateComponents(date.year, date.monthNumber, date.dayOfMonth)
            val adhanTimes = PrayerTimes(coordinates, dateComponents, params)

            val nowMillis = java.lang.System.currentTimeMillis()
            val times = listOf(
                Prayer.FAJR to adhanTimes.fajr,
                Prayer.SUNRISE to adhanTimes.sunrise,
                Prayer.DHUHR to adhanTimes.dhuhr,
                Prayer.ASR to adhanTimes.asr,
                Prayer.MAGHRIB to adhanTimes.maghrib,
                Prayer.ISHA to adhanTimes.isha,
            ).map { (prayer, instant) ->
                PrayerTime(
                    prayer = prayer,
                    time = instant,
                    isNext = false,
                )
            }

            val nextIndex = times.indexOfFirst {
                it.time.toEpochMilliseconds() > nowMillis
            }
            val markedTimes = if (nextIndex >= 0) {
                times.mapIndexed { index, pt ->
                    if (index == nextIndex) pt.copy(isNext = true) else pt
                }
            } else {
                times
            }

            val nextPrayer = markedTimes.firstOrNull { it.isNext }

            PrayerDay(date = date, times = markedTimes, nextPrayer = nextPrayer)
        }

    override fun getNextPrayer(): Flow<PrayerTime?> {
        val now = Instant.fromEpochMilliseconds(java.lang.System.currentTimeMillis())
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return getPrayerTimes(today).map { it.nextPrayer }
    }

    private fun mapCalculationMethod(type: CalculationMethodType): CalculationMethod =
        when (type) {
            CalculationMethodType.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            CalculationMethodType.ISNA -> CalculationMethod.NORTH_AMERICA
            CalculationMethodType.EGYPTIAN -> CalculationMethod.EGYPTIAN
            CalculationMethodType.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
            CalculationMethodType.KARACHI -> CalculationMethod.KARACHI
            CalculationMethodType.DUBAI -> CalculationMethod.DUBAI
            CalculationMethodType.QATAR -> CalculationMethod.QATAR
            CalculationMethodType.KUWAIT -> CalculationMethod.KUWAIT
            CalculationMethodType.MOONSIGHTING_COMMITTEE ->
                CalculationMethod.MOON_SIGHTING_COMMITTEE
            CalculationMethodType.SINGAPORE -> CalculationMethod.SINGAPORE
            CalculationMethodType.NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA
        }

    private fun mapMadhab(type: MadhabType): Madhab =
        when (type) {
            MadhabType.SHAFI -> Madhab.SHAFI
            MadhabType.HANAFI -> Madhab.HANAFI
        }
}
