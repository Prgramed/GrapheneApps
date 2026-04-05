package dev.egallery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.dto.ImmichMapMarker
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class PhotoCluster(
    val lat: Double,
    val lng: Double,
    val markers: List<ImmichMapMarker>,
) {
    val count: Int get() = markers.size
    val isSingle: Boolean get() = markers.size == 1
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val credentialStore: CredentialStore,
    private val immichApi: dev.egallery.api.ImmichPhotoService,
) : ViewModel() {

    private val _markers = MutableStateFlow<List<ImmichMapMarker>>(emptyList())
    val markers: StateFlow<List<ImmichMapMarker>> = _markers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCluster = MutableStateFlow<PhotoCluster?>(null)
    val selectedCluster: StateFlow<PhotoCluster?> = _selectedCluster.asStateFlow()

    // Saved map position (survives navigation)
    var savedZoom: Double = 6.0
    var savedCenterLat: Double? = null
    var savedCenterLon: Double? = null

    fun selectCluster(cluster: PhotoCluster?) {
        _selectedCluster.value = cluster
    }

    fun saveMapPosition(zoom: Double, lat: Double, lon: Double) {
        savedZoom = zoom
        savedCenterLat = lat
        savedCenterLon = lon
    }

    init {
        viewModelScope.launch {
            try {
                // Show cached local markers instantly while API loads
                val cached = withContext(Dispatchers.IO) { mediaDao.getWithLocation() }
                if (cached.isNotEmpty()) {
                    _markers.value = cached.map { entity ->
                        ImmichMapMarker(
                            id = entity.nasId,
                            lat = entity.lat ?: 0.0,
                            lon = entity.lng ?: 0.0,
                        )
                    }
                    Timber.d("Showing ${cached.size} cached map markers")
                }

                // Always fetch from API (time bucket sync doesn't include lat/lng)
                val apiMarkers = withContext(Dispatchers.IO) {
                    immichApi.getMapMarkers()
                }
                _markers.value = apiMarkers
                Timber.d("Loaded ${apiMarkers.size} map markers from API")

                // Cache lat/lng into Room for instant display next time
                withContext(Dispatchers.IO) {
                    for (marker in apiMarkers) {
                        if (marker.lat != 0.0 || marker.lon != 0.0) {
                            mediaDao.updateLatLng(marker.id, marker.lat, marker.lon)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load map markers from API")
                // Keep whatever cached markers we have
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cluster(markers: List<ImmichMapMarker>, cellSize: Double = 1.0): List<PhotoCluster> {
        val groups = markers.groupBy { marker ->
            val latCell = (marker.lat / cellSize).toInt()
            val lngCell = (marker.lon / cellSize).toInt()
            latCell to lngCell
        }
        return groups.map { (_, groupMarkers) ->
            val avgLat = groupMarkers.map { it.lat }.average()
            val avgLng = groupMarkers.map { it.lon }.average()
            PhotoCluster(lat = avgLat, lng = avgLng, markers = groupMarkers)
        }
    }

    fun thumbnailUrl(assetId: String): String =
        ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, assetId)
}
