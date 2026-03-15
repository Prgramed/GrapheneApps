package com.prgramed.eprayer.data.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.prgramed.eprayer.data.location.NativeLocationProvider
import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class PrayerWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val nativeLocationProvider: NativeLocationProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.getUserPreferences().first()

        val lat: Double
        val lon: Double
        when (prefs.locationMode) {
            LocationMode.GPS -> {
                val location = nativeLocationProvider.getLastKnown()
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                } else {
                    // Fallback to Mecca if no location available
                    lat = 21.4225
                    lon = 39.8262
                }
            }
            LocationMode.MANUAL -> {
                lat = prefs.manualLatitude ?: 21.4225
                lon = prefs.manualLongitude ?: 39.8262
            }
        }

        val calendar = Calendar.getInstance()
        val dateComponents = DateComponents(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )

        val coordinates = Coordinates(lat, lon)
        val params = mapCalculationMethod(prefs.calculationMethod).parameters.copy(
            madhab = mapMadhab(prefs.madhab),
        )
        val prayerTimes = PrayerTimes(coordinates, dateComponents, params)

        val nowMillis = java.lang.System.currentTimeMillis()
        val prayers = listOf(
            "Fajr" to prayerTimes.fajr.toEpochMilliseconds(),
            "Dhuhr" to prayerTimes.dhuhr.toEpochMilliseconds(),
            "Asr" to prayerTimes.asr.toEpochMilliseconds(),
            "Maghrib" to prayerTimes.maghrib.toEpochMilliseconds(),
            "Isha" to prayerTimes.isha.toEpochMilliseconds(),
        )

        val nextPrayer = prayers.firstOrNull { (_, millis) -> millis > nowMillis }
            ?: prayers.first()

        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRAYER_NAME, nextPrayer.first)
            .putLong(KEY_PRAYER_TIME_MILLIS, nextPrayer.second)
            .apply()

        return Result.success()
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

    companion object {
        const val PREFS_NAME = "eprayer_widget_data"
        const val KEY_PRAYER_NAME = "next_prayer_name"
        const val KEY_PRAYER_TIME_MILLIS = "next_prayer_time_millis"
        const val WORK_NAME = "eprayer_widget_refresh"
    }
}
