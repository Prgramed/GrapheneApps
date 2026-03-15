package com.prgramed.eprayer.data.location

import com.prgramed.eprayer.domain.model.LocationInfo
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val nativeLocationProvider: NativeLocationProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LocationRepository {

    override fun getCurrentLocation(): Flow<LocationInfo> =
        userPreferencesRepository.getUserPreferences().flatMapLatest { prefs ->
            when (prefs.locationMode) {
                LocationMode.GPS -> nativeLocationProvider.locationUpdates()
                LocationMode.MANUAL -> {
                    val lat = prefs.manualLatitude ?: 0.0
                    val lon = prefs.manualLongitude ?: 0.0
                    flowOf(LocationInfo(lat, lon, prefs.manualCityName))
                }
            }
        }

    override suspend fun getLastKnownLocation(): LocationInfo? =
        nativeLocationProvider.getLastKnown()
}
