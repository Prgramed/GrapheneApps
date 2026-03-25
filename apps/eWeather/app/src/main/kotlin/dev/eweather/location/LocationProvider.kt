package dev.eweather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class LocationResult {
    data class Success(val lat: Double, val lon: Double) : LocationResult()
    data object PermissionDenied : LocationResult()
    data object Unavailable : LocationResult()
}

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun requestCurrentLocation(): LocationResult {
        if (!checkPermissions()) return LocationResult.PermissionDenied

        val manager = locationManager

        // 1. Try cached locations first (instant)
        val lastKnown = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (lastKnown != null) {
            saveToStore(lastKnown.latitude, lastKnown.longitude)
            return LocationResult.Success(lastKnown.latitude, lastKnown.longitude)
        }

        // 2. Request from NETWORK_PROVIDER (fast, battery-friendly)
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            val networkFix = withTimeoutOrNull(10_000L) {
                requestSingleFix(manager, LocationManager.NETWORK_PROVIDER)
            }
            if (networkFix != null) {
                saveToStore(networkFix.latitude, networkFix.longitude)
                return LocationResult.Success(networkFix.latitude, networkFix.longitude)
            }
        }

        // 3. Fallback to GPS_PROVIDER (slower but more accurate)
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gpsFix = withTimeoutOrNull(20_000L) {
                requestSingleFix(manager, LocationManager.GPS_PROVIDER)
            }
            if (gpsFix != null) {
                saveToStore(gpsFix.latitude, gpsFix.longitude)
                return LocationResult.Success(gpsFix.latitude, gpsFix.longitude)
            }
        }

        // 4. All failed
        Timber.w("Location unavailable — no provider returned a fix")
        return LocationResult.Unavailable
    }

    suspend fun getLastKnownFromStore(): Pair<Double, Double>? {
        val prefs = dataStore.data.first()
        val lat = prefs[KEY_LAST_LAT] ?: return null
        val lon = prefs[KEY_LAST_LON] ?: return null
        return lat to lon
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(
        manager: LocationManager,
        provider: String,
    ): Location = suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (cont.isActive) cont.resume(location)
            }

            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())

        cont.invokeOnCancellation {
            manager.removeUpdates(listener)
        }
    }

    private suspend fun saveToStore(lat: Double, lon: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_LAT] = lat
            prefs[KEY_LAST_LON] = lon
        }
    }

    companion object {
        private val KEY_LAST_LAT = doublePreferencesKey("last_gps_lat")
        private val KEY_LAST_LON = doublePreferencesKey("last_gps_lon")
    }
}
