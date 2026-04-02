package com.prgramed.eprayer.data.location

import com.prgramed.eprayer.domain.model.LocationInfo
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val nativeLocationProvider: NativeLocationProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LocationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Single shared GPS flow — stops 5s after last subscriber disconnects
    private val sharedLocation: Flow<LocationInfo> =
        userPreferencesRepository.getUserPreferences().flatMapLatest { prefs ->
            when (prefs.locationMode) {
                LocationMode.GPS -> nativeLocationProvider.locationUpdates()
                LocationMode.MANUAL -> {
                    val lat = prefs.manualLatitude ?: 0.0
                    val lon = prefs.manualLongitude ?: 0.0
                    flowOf(LocationInfo(lat, lon, prefs.manualCityName))
                }
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(0), replay = 1)

    override fun getCurrentLocation(): Flow<LocationInfo> = sharedLocation

    override suspend fun getLastKnownLocation(): LocationInfo? =
        nativeLocationProvider.getLastKnown()
}
