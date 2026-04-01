package dev.egallery.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.ImmichPhotoMapper
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.domain.model.MediaItem
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<MediaItem>>(emptyList())
    val results: StateFlow<List<MediaItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()

    // Filters
    private val _selectedMediaType = MutableStateFlow<String?>(null)
    val selectedMediaType: StateFlow<String?> = _selectedMediaType.asStateFlow()

    private val _selectedCountry = MutableStateFlow<String?>(null)
    val selectedCountry: StateFlow<String?> = _selectedCountry.asStateFlow()

    private val _selectedCity = MutableStateFlow<String?>(null)
    val selectedCity: StateFlow<String?> = _selectedCity.asStateFlow()

    private val _fromDate = MutableStateFlow<Long?>(null)
    val fromDate: StateFlow<Long?> = _fromDate.asStateFlow()

    private val _toDate = MutableStateFlow<Long?>(null)
    val toDate: StateFlow<Long?> = _toDate.asStateFlow()

    // Suggestions
    private val _countrySuggestions = MutableStateFlow<List<String>>(emptyList())
    val countrySuggestions: StateFlow<List<String>> = _countrySuggestions.asStateFlow()

    private val _citySuggestions = MutableStateFlow<List<String>>(emptyList())
    val citySuggestions: StateFlow<List<String>> = _citySuggestions.asStateFlow()

    val recentSearches = preferencesRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            query.debounce(500).collect { q ->
                if (q.length >= 2) doSearch()
                else if (q.isBlank()) { _results.value = emptyList(); _resultCount.value = 0 }
            }
        }
        viewModelScope.launch {
            try { _countrySuggestions.value = immichApi.getSearchSuggestions("country") }
            catch (_: Exception) {}
        }
    }

    fun setMediaTypeFilter(type: String?) { _selectedMediaType.value = type; doSearchIfReady() }
    fun setCountryFilter(country: String?) {
        _selectedCountry.value = country
        if (country != null) {
            viewModelScope.launch {
                try { _citySuggestions.value = immichApi.getSearchSuggestions("city") }
                catch (_: Exception) {}
            }
        } else { _citySuggestions.value = emptyList(); _selectedCity.value = null }
        doSearchIfReady()
    }
    fun setCityFilter(city: String?) { _selectedCity.value = city; doSearchIfReady() }
    fun setDateRange(from: Long?, to: Long?) { _fromDate.value = from; _toDate.value = to; doSearchIfReady() }
    fun clearFilters() {
        _selectedMediaType.value = null; _selectedCountry.value = null
        _selectedCity.value = null; _fromDate.value = null; _toDate.value = null
        doSearchIfReady()
    }
    fun saveRecentSearch(q: String) { if (q.length >= 2) viewModelScope.launch { preferencesRepository.addRecentSearch(q) } }
    fun clearRecentSearches() { viewModelScope.launch { preferencesRepository.clearRecentSearches() } }

    private fun doSearchIfReady() {
        if (query.value.length >= 2 || hasActiveFilters()) viewModelScope.launch { doSearch() }
    }
    private fun hasActiveFilters() = _selectedMediaType.value != null || _selectedCountry.value != null ||
        _selectedCity.value != null || _fromDate.value != null || _toDate.value != null

    private suspend fun doSearch() {
        _isSearching.value = true
        val q = query.value.trim()
        android.util.Log.d("SearchDebug", "doSearch called with query='$q'")
        try {
            val items = metadataSearch(q)
            android.util.Log.d("SearchDebug", "metadataSearch returned ${items.size} items")
            _results.value = items
            _resultCount.value = items.size
        } catch (e: Exception) {
            android.util.Log.e("SearchDebug", "Search failed: ${e.message}", e)
            _results.value = emptyList(); _resultCount.value = 0
        } finally { _isSearching.value = false }
    }

    private suspend fun metadataSearch(query: String): List<MediaItem> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        fields["page"] = JsonPrimitive(1)
        fields["size"] = JsonPrimitive(200)
        if (query.isNotBlank()) fields["originalFileName"] = JsonPrimitive(query)
        _selectedMediaType.value?.let { fields["type"] = JsonPrimitive(it) }
        _selectedCountry.value?.let { fields["country"] = JsonPrimitive(it) }
        _selectedCity.value?.let { fields["city"] = JsonPrimitive(it) }
        _fromDate.value?.let { fields["takenAfter"] = JsonPrimitive(dateFormat.format(java.util.Date(it))) }
        _toDate.value?.let { fields["takenBefore"] = JsonPrimitive(dateFormat.format(java.util.Date(it))) }
        return immichApi.searchMetadata(JsonObject(fields)).assets.items
            .mapNotNull { ImmichPhotoMapper.run { it.toDomain() } }
    }

    fun thumbnailUrl(nasId: String) = ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
}
