package dev.egallery.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.ImmichPhotoMapper
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
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
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val personDao: PersonDao,
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
            personDao.getAll().collect { persons ->
                _person.value = persons.find { it.id == personId }?.toDomain()
            }
        }
        loadPersonPhotos()
    }

    private fun loadPersonPhotos() {
        viewModelScope.launch {
            try {
                val body = kotlinx.serialization.json.buildJsonObject {
                    put("personIds", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(personId))))
                    put("page", kotlinx.serialization.json.JsonPrimitive(1))
                    put("size", kotlinx.serialization.json.JsonPrimitive(500))
                }
                val response = immichApi.searchMetadata(body)
                _photos.value = response.assets.items.mapNotNull {
                    ImmichPhotoMapper.run { it.toDomain() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load person $personId photos")
            }
            _loading.value = false
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String = "", isSharedSpace: Boolean = false): String =
        ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
}
