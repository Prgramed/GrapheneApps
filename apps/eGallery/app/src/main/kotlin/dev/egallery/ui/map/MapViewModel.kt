package dev.egallery.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.toDomain
import dev.egallery.domain.model.MediaItem
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoCluster(
    val lat: Double,
    val lng: Double,
    val items: List<MediaItem>,
) {
    val count: Int get() = items.size
    val isSingle: Boolean get() = items.size == 1
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _geoItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val geoItems: StateFlow<List<MediaItem>> = _geoItems.asStateFlow()

    init {
        viewModelScope.launch {
            _geoItems.value = mediaDao.getWithLocation().map { it.toDomain() }
        }
    }

    fun clusterItems(items: List<MediaItem>, zoomLevel: Double): List<PhotoCluster> {
        if (items.isEmpty()) return emptyList()

        // Grid-based clustering: cell size decreases with zoom
        val cellSize = 360.0 / (1 shl zoomLevel.toInt().coerceIn(1, 20))

        val groups = items.groupBy { item ->
            val latCell = ((item.lat ?: 0.0) / cellSize).toInt()
            val lngCell = ((item.lng ?: 0.0) / cellSize).toInt()
            latCell to lngCell
        }

        return groups.map { (_, groupItems) ->
            val avgLat = groupItems.mapNotNull { it.lat }.average()
            val avgLng = groupItems.mapNotNull { it.lng }.average()
            PhotoCluster(lat = avgLat, lng = avgLng, items = groupItems)
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }
}
