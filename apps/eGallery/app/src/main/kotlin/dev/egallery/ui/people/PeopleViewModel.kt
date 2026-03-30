package dev.egallery.ui.people

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.repository.PersonRepository
import dev.egallery.domain.model.Person
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    personRepository: PersonRepository,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    val people: Flow<List<Person>> = personRepository.observeAll().map { persons ->
        val named = persons.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() }
        val unnamed = persons.filter { it.name.isBlank() }
        named + unnamed
    }

    fun coverThumbnailUrl(coverPhotoId: String?, isSharedSpace: Boolean = false): String? {
        if (coverPhotoId == null) return null
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, coverPhotoId)
    }
}
