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
import kotlin.math.cos
import kotlin.math.sqrt

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

    private var cachedCityName: String? = null
    private var cachedCityLat: Double = Double.NaN
    private var cachedCityLon: Double = Double.NaN

    private fun Location.toLocationInfo(): LocationInfo {
        val cityName = getCachedOrResolveCity(latitude, longitude)
        return LocationInfo(latitude = latitude, longitude = longitude, cityName = cityName)
    }

    private fun getCachedOrResolveCity(lat: Double, lon: Double): String? {
        if (cachedCityName != null && !cachedCityLat.isNaN()) {
            val dist = approxDistance(lat, lon, cachedCityLat, cachedCityLon)
            if (dist < 0.1) return cachedCityName // ~10km — skip re-resolve
        }
        val name = resolveCity(lat, lon)
        cachedCityName = name
        cachedCityLat = lat
        cachedCityLon = lon
        return name
    }

    private fun resolveCity(lat: Double, lon: Double): String? {
        // Try Geocoder first (works on devices with Google backend)
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val name = addresses?.firstOrNull()?.locality
                    ?: addresses?.firstOrNull()?.subAdminArea
                    ?: addresses?.firstOrNull()?.adminArea
                if (name != null) return name
            }
        } catch (_: Exception) { }

        // Fall back to nearest known city
        return findNearestCity(lat, lon)
    }

    private fun findNearestCity(lat: Double, lon: Double): String? {
        var bestName: String? = null
        var bestDist = Double.MAX_VALUE
        for ((name, cLat, cLon) in CITIES) {
            val dist = approxDistance(lat, lon, cLat, cLon)
            if (dist < bestDist) {
                bestDist = dist
                bestName = name
            }
        }
        // Only return if within ~100km
        return if (bestDist < 1.0) bestName else null
    }

    private fun approxDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 300_000L // 5 minutes
        private const val MIN_UPDATE_DISTANCE_M = 100f

        // Major cities for nearest-match fallback (lat, lon)
        private val CITIES = listOf(
            Triple("Mecca", 21.4225, 39.8262),
            Triple("Medina", 24.4672, 39.6024),
            Triple("Riyadh", 24.7136, 46.6753),
            Triple("Jeddah", 21.5433, 39.1728),
            Triple("Dubai", 25.2048, 55.2708),
            Triple("Abu Dhabi", 24.4539, 54.3773),
            Triple("Doha", 25.2854, 51.5310),
            Triple("Kuwait City", 29.3759, 47.9774),
            Triple("Cairo", 30.0444, 31.2357),
            Triple("Alexandria", 31.2001, 29.9187),
            Triple("Istanbul", 41.0082, 28.9784),
            Triple("Ankara", 39.9334, 32.8597),
            Triple("Amman", 31.9454, 35.9284),
            Triple("Beirut", 33.8938, 35.5018),
            Triple("Baghdad", 33.3153, 44.3661),
            Triple("Tehran", 35.6892, 51.3890),
            Triple("Islamabad", 33.6844, 73.0479),
            Triple("Karachi", 24.8607, 67.0011),
            Triple("Lahore", 31.5204, 74.3587),
            Triple("Dhaka", 23.8103, 90.4125),
            Triple("Jakarta", 6.2088, 106.8456),
            Triple("Kuala Lumpur", 3.1390, 101.6869),
            Triple("London", 51.5074, -0.1278),
            Triple("Paris", 48.8566, 2.3522),
            Triple("Berlin", 52.5200, 13.4050),
            Triple("New York", 40.7128, -74.0060),
            Triple("Los Angeles", 34.0522, -118.2437),
            Triple("Toronto", 43.6532, -79.3832),
            Triple("Sydney", -33.8688, 151.2093),
            Triple("Lagos", 6.5244, 3.3792),
            Triple("Casablanca", 33.5731, -7.5898),
            Triple("Tunis", 36.8065, 10.1815),
            Triple("Algiers", 36.7538, 3.0588),
            Triple("Khartoum", 15.5007, 32.5599),
            Triple("Dammam", 26.3927, 49.9777),
            Triple("Muscat", 23.5880, 58.3829),
            Triple("Manama", 26.2285, 50.5860),
            Triple("Jerusalem", 31.7683, 35.2137),
            Triple("Damascus", 33.5138, 36.2765),
            Triple("Singapore", 1.3521, 103.8198),
            Triple("Stockholm", 59.3293, 18.0686),
            Triple("Oslo", 59.9139, 10.7522),
            Triple("Copenhagen", 55.6761, 12.5683),
            Triple("Amsterdam", 52.3676, 4.9041),
            Triple("Brussels", 50.8503, 4.3517),
            Triple("Madrid", 40.4168, -3.7038),
            Triple("Rome", 41.9028, 12.4964),
            Triple("Vienna", 48.2082, 16.3738),
            Triple("Moscow", 55.7558, 37.6173),
            Triple("Gothenburg", 57.7089, 11.9746),
            Triple("Malmö", 55.6050, 13.0038),
            Triple("Tokyo", 35.6762, 139.6503),
            Triple("Beijing", 39.9042, 116.4074),
            Triple("Mumbai", 19.0760, 72.8777),
            Triple("New Delhi", 28.6139, 77.2090),
        )
    }
}
