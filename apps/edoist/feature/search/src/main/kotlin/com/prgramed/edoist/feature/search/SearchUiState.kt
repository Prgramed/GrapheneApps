package com.prgramed.edoist.feature.search

import com.prgramed.edoist.domain.model.Filter
import com.prgramed.edoist.domain.model.Label
import com.prgramed.edoist.domain.model.Task

data class SearchUiState(
    val query: String = "",
    val results: List<Task> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val labels: List<Label> = emptyList(),
    val isSearching: Boolean = false,
)
