package com.prgramed.edoist.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.repository.FilterRepository
import com.prgramed.edoist.domain.repository.LabelRepository
import com.prgramed.edoist.domain.usecase.SearchTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTasksUseCase: SearchTasksUseCase,
    filterRepository: FilterRepository,
    labelRepository: LabelRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    private val debouncedQuery = queryFlow
        .debounce(300)
        .distinctUntilChanged()

    private val searchResults = debouncedQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            flowOf(emptyList())
        } else {
            searchTasksUseCase(query)
        }
    }

    private val isSearching = combine(queryFlow, debouncedQuery) { current, debounced ->
        current.isNotBlank() && current != debounced
    }

    val uiState = combine(
        queryFlow,
        searchResults,
        filterRepository.observeAll(),
        labelRepository.observeAll(),
        isSearching,
    ) { query, results, filters, labels, searching ->
        SearchUiState(
            query = query,
            results = results,
            filters = filters,
            labels = labels,
            isSearching = searching,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun clearSearch() {
        queryFlow.value = ""
    }
}
