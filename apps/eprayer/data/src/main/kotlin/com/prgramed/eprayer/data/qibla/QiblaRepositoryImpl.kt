package com.prgramed.eprayer.data.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import com.prgramed.eprayer.data.sensor.CompassSensorManager
import com.prgramed.eprayer.domain.model.QiblaDirection
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.QiblaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QiblaRepositoryImpl @Inject constructor(
    private val locationRepository: LocationRepository,
    private val compassSensorManager: CompassSensorManager,
) : QiblaRepository {

    override fun getQiblaDirection(): Flow<QiblaDirection> =
        combine(
            locationRepository.getCurrentLocation(),
            compassSensorManager.headingUpdates(),
        ) { location, heading ->
            val qiblaBearing = Qibla(
                Coordinates(location.latitude, location.longitude),
            ).direction
            val relativeAngle = (qiblaBearing - heading + 360) % 360

            QiblaDirection(
                qiblaBearing = qiblaBearing,
                deviceHeading = heading,
                relativeAngle = relativeAngle,
            )
        }
}
