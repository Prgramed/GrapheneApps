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

    init {
        viewModelScope.launch {
            try {
                // Try local DB first (instant)
                val entities = withContext(Dispatchers.IO) { mediaDao.getWithLocation() }
                if (entities.isNotEmpty()) {
                    _markers.value = entities.map { entity ->
                        ImmichMapMarker(
                            id = entity.nasId,
                            lat = entity.lat ?: 0.0,
                            lon = entity.lng ?: 0.0,
                        )
                    }
                    Timber.d("Loaded ${_markers.value.size} map markers from local DB")
                } else {
                    // Fallback to API if no local geotagged data
                    val apiMarkers = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                        immichApi.getMapMarkers()
                    }
                    _markers.value = apiMarkers ?: emptyList()
                    Timber.d("Loaded ${_markers.value.size} map markers from API (no local data)")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load map markers")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cluster(markers: List<ImmichMapMarker>, precision: Int = 3): List<PhotoCluster> {
        val groups = markers.groupBy { marker ->
            val latCell = (marker.lat * Math.pow(10.0, precision.toDouble())).toInt()
            val lngCell = (marker.lon * Math.pow(10.0, precision.toDouble())).toInt()
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
