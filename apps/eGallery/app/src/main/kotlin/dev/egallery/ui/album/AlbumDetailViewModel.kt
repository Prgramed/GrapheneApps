package dev.egallery.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.repository.AlbumRepository
import dev.egallery.data.repository.MediaRepository
import dev.egallery.domain.model.Album
import dev.egallery.domain.model.MediaItem
import dev.egallery.sync.NasSyncEngine
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val mediaRepository: MediaRepository,
    private val albumMediaDao: AlbumMediaDao,
    private val syncEngine: NasSyncEngine,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""

    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    val photos: Flow<List<MediaItem>> = mediaRepository.observeAlbum(albumId)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _album.value = albumRepository.getById(albumId)

            // Sync album items from API → populate album_media join table
            try {
                syncEngine.syncAlbumItems(albumId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync album $albumId items")
            }
            _loading.value = false
        }
    }

    // Multi-select for remove
    private val _selectedNasIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNasIds: StateFlow<Set<String>> = _selectedNasIds.asStateFlow()

    val isMultiSelectMode: Boolean get() = _selectedNasIds.value.isNotEmpty()

    fun toggleSelection(nasId: String) {
        _selectedNasIds.value = _selectedNasIds.value.let {
            if (nasId in it) it - nasId else it + nasId
        }
    }

    fun clearSelection() {
        _selectedNasIds.value = emptySet()
    }

    fun removeSelectedFromAlbum() {
        val ids = _selectedNasIds.value.toList()
        viewModelScope.launch {
            for (nasId in ids) {
                albumMediaDao.deleteByAlbumAndNasId(albumId, nasId)
            }
            _selectedNasIds.value = emptySet()
            Timber.d("Removed ${ids.size} items from album $albumId")
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }
}
