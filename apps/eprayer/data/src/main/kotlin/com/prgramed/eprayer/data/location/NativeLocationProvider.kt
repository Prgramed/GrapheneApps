package com.prgramed.eprayer.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.prgramed.eprayer.domain.model.LocationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<LocationInfo> = callbackFlow {
        val manager = locationManager

        val lastKnown = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            trySend(lastKnown.toLocationInfo())
        }

        val listener = LocationListener { location ->
            trySend(location.toLocationInfo())
        }

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                if (lastKnown == null) close()
                awaitClose {}
                return@callbackFlow
            }
        }

        manager.requestLocationUpdates(
            provider,
            MIN_UPDATE_INTERVAL_MS,
            MIN_UPDATE_DISTANCE_M,
            listener,
            Looper.getMainLooper(),
        )

        awaitClose { manager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnown(): LocationInfo? {
        val manager = locationManager
        val location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return location?.toLocationInfo()
    }

    private fun Location.toLocationInfo(): LocationInfo {
        val cityName = reverseGeocode(latitude, longitude)
        return LocationInfo(latitude = latitude, longitude = longitude, cityName = cityName)
    }

    private fun reverseGeocode(lat: Double, lon: Double): String? = try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        addresses?.firstOrNull()?.locality
            ?: addresses?.firstOrNull()?.subAdminArea
            ?: addresses?.firstOrNull()?.adminArea
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 60_000L
        private const val MIN_UPDATE_DISTANCE_M = 100f
    }
}
