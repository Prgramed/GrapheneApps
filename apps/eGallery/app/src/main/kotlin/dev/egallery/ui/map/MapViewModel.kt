package dev.egallery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.ImmichPhotoService
import dev.egallery.api.dto.ImmichMapMarker
import dev.egallery.data.CredentialStore
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _markers = MutableStateFlow<List<ImmichMapMarker>>(emptyList())
    val markers: StateFlow<List<ImmichMapMarker>> = _markers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val markers = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    immichApi.getMapMarkers()
                }
                _markers.value = markers ?: emptyList()
                Timber.d("Loaded ${_markers.value.size} map markers")
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
