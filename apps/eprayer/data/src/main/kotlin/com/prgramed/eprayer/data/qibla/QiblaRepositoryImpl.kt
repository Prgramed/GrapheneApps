package com.prgramed.eprayer.data.qibla

import android.hardware.GeomagneticField
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import com.prgramed.eprayer.data.sensor.CompassSensorManager
import com.prgramed.eprayer.domain.model.LocationInfo
import com.prgramed.eprayer.domain.model.QiblaDirection
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.QiblaRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QiblaRepositoryImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    private val compassSensorManager: CompassSensorManager,
    private val userPreferencesRepository: UserPreferencesRepository,
) : QiblaRepository {

    override fun getQiblaDirection(): Flow<QiblaDirection> {
        // Seed location with saved prefs so compass doesn't wait for GPS cold start
        val locationFlow = locationRepository.getCurrentLocation().onStart {
            val lastKnown = locationRepository.getLastKnownLocation()
            if (lastKnown != null) {
                emit(lastKnown)
            } else {
                // Use saved prayer location as fallback
                val prefs = userPreferencesRepository.getUserPreferences().first()
                val lat = prefs.manualLatitude
                val lon = prefs.manualLongitude
                if (lat != null && lon != null) {
                    emit(LocationInfo(lat, lon, prefs.manualCityName))
                }
            }
        }

        return combine(
            locationFlow,
            compassSensorManager.headingUpdates(),
        ) { location, compassReading ->
            val geoField = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                0f,
                System.currentTimeMillis(),
            )
            val trueHeading = (compassReading.heading + geoField.declination + 360f) % 360f

            val qiblaBearing = Qibla(
                Coordinates(location.latitude, location.longitude),
            ).direction
            val relativeAngle = (qiblaBearing - trueHeading + 360) % 360

            QiblaDirection(
                qiblaBearing = qiblaBearing,
                deviceHeading = trueHeading,
                relativeAngle = relativeAngle,
                needsCalibration = compassReading.needsCalibration,
            )
        }
    }
}
