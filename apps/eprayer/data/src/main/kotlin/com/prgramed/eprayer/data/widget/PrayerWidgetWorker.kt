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
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
            .putString(KEY_NEXT_NAME, nextPrayer.first)
            .putLong(KEY_NEXT_TIME, nextPrayer.second)
            .putLong(KEY_FAJR, prayers[0].second)
            .putLong(KEY_DHUHR, prayers[1].second)
            .putLong(KEY_ASR, prayers[2].second)
            .putLong(KEY_MAGHRIB, prayers[3].second)
            .putLong(KEY_ISHA, prayers[4].second)
            .putString(KEY_CALC_METHOD, prefs.calculationMethod.name)
            .putString(KEY_MADHAB, prefs.madhab.name)
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()

        // Notify AppWidgetManager to update widget UI
        try {
            val widgetClass = Class.forName("com.prgramed.eprayer.feature.widget.PrayerWidgetReceiver")
            val widgetManager = AppWidgetManager.getInstance(applicationContext)
            val ids = widgetManager.getAppWidgetIds(ComponentName(applicationContext, widgetClass))
            if (ids.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    component = ComponentName(applicationContext, widgetClass)
                }
                applicationContext.sendBroadcast(intent)
            }
        } catch (_: Exception) { }

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
        const val KEY_NEXT_NAME = "next_prayer_name"
        const val KEY_NEXT_TIME = "next_prayer_time_millis"
        const val KEY_FAJR = "fajr_millis"
        const val KEY_DHUHR = "dhuhr_millis"
        const val KEY_ASR = "asr_millis"
        const val KEY_MAGHRIB = "maghrib_millis"
        const val KEY_ISHA = "isha_millis"
        const val KEY_CALC_METHOD = "calc_method"
        const val KEY_MADHAB = "madhab"
        const val KEY_LAT = "latitude"
        const val KEY_LON = "longitude"
        const val WORK_NAME = "eprayer_widget_refresh"
    }
}
