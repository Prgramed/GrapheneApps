package dev.egallery.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.TagDao
import dev.egallery.data.db.entity.TagEntity
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.data.repository.toDomain
import dev.egallery.domain.model.MediaItem
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val tagDao: TagDao,
    private val credentialStore: CredentialStore,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    // Fix #8: track search job for cancellation
    private var searchJob: Job? = null

    private val _results = MutableStateFlow<List<MediaItem>>(emptyList())
    val results: StateFlow<List<MediaItem>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val recentSearches: StateFlow<List<String>> = preferencesRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter state
    val availableTags: StateFlow<List<TagEntity>> = tagDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTagId = MutableStateFlow<String?>(null)
    val selectedTagId: StateFlow<String?> = _selectedTagId.asStateFlow()

    private val _selectedMediaType = MutableStateFlow<String?>(null) // "PHOTO", "VIDEO", or null
    val selectedMediaType: StateFlow<String?> = _selectedMediaType.asStateFlow()

    private val _fromDate = MutableStateFlow<Long?>(null)
    val fromDate: StateFlow<Long?> = _fromDate.asStateFlow()

    private val _toDate = MutableStateFlow<Long?>(null)
    val toDate: StateFlow<Long?> = _toDate.asStateFlow()

    init {
        viewModelScope.launch {
            query.debounce(300).collect { q ->
                if (q.length >= 2) {
                    performSearch(q)
                } else if (hasActiveFilters()) {
                    performSearch("")
                } else {
                    _results.value = emptyList()
                }
            }
        }
    }

    fun setTagFilter(tagId: String?) {
        _selectedTagId.value = tagId
        rerunSearch()
    }

    fun setMediaTypeFilter(type: String?) {
        _selectedMediaType.value = type
        rerunSearch()
    }

    fun setDateRange(from: Long?, to: Long?) {
        _fromDate.value = from
        _toDate.value = to
        rerunSearch()
    }

    fun clearFilters() {
        _selectedTagId.value = null
        _selectedMediaType.value = null
        _fromDate.value = null
        _toDate.value = null
        rerunSearch()
    }

    private fun hasActiveFilters(): Boolean =
        _selectedTagId.value != null || _selectedMediaType.value != null ||
            _fromDate.value != null || _toDate.value != null

    private fun rerunSearch() {
        val q = query.value
        if (q.length >= 2 || hasActiveFilters()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(q) }
        }
    }

    private suspend fun performSearch(q: String) {
        _searching.value = true

        // Search local Room DB (data is already synced from Immich)
        val searchQuery = if (q.length >= 2) q else "%"
        val localResults = mediaDao.searchFiltered(
            query = searchQuery,
            mediaType = _selectedMediaType.value,
            fromDate = _fromDate.value,
            toDate = _toDate.value,
            tagId = _selectedTagId.value,
        ).map { it.toDomain() }
        _results.value = localResults

        _searching.value = false
    }

    fun saveRecentSearch(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            preferencesRepository.addRecentSearch(q)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            preferencesRepository.clearRecentSearches()
        }
    }

    fun thumbnailUrl(nasId: String, cacheKey: String, isSharedSpace: Boolean = false): String {
        return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
    }
}
