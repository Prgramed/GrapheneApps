package dev.egallery.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.repository.AlbumRepository
import dev.egallery.domain.model.Album
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    val albums: Flow<List<Album>> = albumRepository.observeAll()

    fun coverThumbnailUrl(coverPhotoId: String?, cacheKey: String = "", isSharedSpace: Boolean = false): String? {
        if (coverPhotoId == null) return null
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, coverPhotoId)
    }

    fun createAlbum(name: String) {
        viewModelScope.launch { albumRepository.create(name) }
    }

    fun renameAlbum(id: String, name: String) {
        viewModelScope.launch { albumRepository.rename(id, name) }
    }

    fun deleteAlbum(id: String) {
        viewModelScope.launch { albumRepository.delete(id) }
    }
}
