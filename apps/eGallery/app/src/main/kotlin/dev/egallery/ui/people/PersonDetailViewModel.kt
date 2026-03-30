package dev.egallery.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.PersonDao
import dev.egallery.data.repository.toDomain
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.Person
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val credentialStore: CredentialStore,
    private val personDao: PersonDao,
    private val mediaDao: MediaDao,
) : ViewModel() {

    private val personId: String = savedStateHandle["personId"] ?: ""

    private val _person = MutableStateFlow<Person?>(null)
    val person: StateFlow<Person?> = _person.asStateFlow()

    private val _photos = MutableStateFlow<List<MediaItem>>(emptyList())
    val photos: StateFlow<List<MediaItem>> = _photos.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            // Load person info from Room
            personDao.getAll().collect { persons ->
                _person.value = persons.find { it.id == personId }?.toDomain()
            }
        }
        loadPersonPhotos()
    }

    private fun loadPersonPhotos() {
        viewModelScope.launch {
            try {
                // Load from Room DB (data synced from Immich already)
                val allMedia = mediaDao.getAllNasIdsOrdered()
                // Person photos are loaded from the synced Room data
                // For now, show empty until album-based person sync is implemented
                _photos.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load person $personId photos")
            }
            _loading.value = false
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }
}
