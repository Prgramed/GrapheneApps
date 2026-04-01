package dev.equran.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.domain.model.SearchResult
import dev.equran.domain.repository.QuranRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val preferencesRepository: QuranPreferencesRepository,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isSmartMode = MutableStateFlow(false)
    val isSmartMode: StateFlow<Boolean> = _isSmartMode.asStateFlow()

    val hasServerUrl: StateFlow<Boolean> = preferencesRepository.settings
        .map { it.quranIndexServerUrl.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            query.debounce(400).collect { q ->
                if (q.length >= 2) {
                    doSearch(q)
                } else if (q.isBlank()) {
                    _results.value = emptyList()
                }
            }
        }
    }

    fun toggleSearchMode() {
        _isSmartMode.value = !_isSmartMode.value
        val q = query.value
        if (q.length >= 2) viewModelScope.launch { doSearch(q) }
    }

    private suspend fun doSearch(q: String) {
        _isSearching.value = true
        _results.value = if (_isSmartMode.value) {
            val smart = quranRepository.semanticSearch(q)
            smart.ifEmpty { quranRepository.textSearch(q) }
        } else {
            quranRepository.textSearch(q)
        }
        _isSearching.value = false
    }
}
