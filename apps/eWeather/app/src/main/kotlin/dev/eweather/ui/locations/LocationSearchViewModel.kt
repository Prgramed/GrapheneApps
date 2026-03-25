package dev.eweather.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.eweather.data.api.OpenMeteoGeocodingService
import dev.eweather.data.api.OpenMeteoMapper
import dev.eweather.data.api.dto.GeocodingResultDto
import dev.eweather.domain.repository.LocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LocationSearchUiState {
    data object Idle : LocationSearchUiState
    data object Loading : LocationSearchUiState
    data class Results(val results: List<GeocodingResultDto>) : LocationSearchUiState
    data class Empty(val query: String) : LocationSearchUiState
    data class Error(val message: String) : LocationSearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocationSearchViewModel @Inject constructor(
    private val geocodingService: OpenMeteoGeocodingService,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<LocationSearchUiState>(LocationSearchUiState.Idle)
    val uiState: StateFlow<LocationSearchUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack = _navigateBack.asSharedFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    flow {
                        if (query.length < 2) {
                            emit(LocationSearchUiState.Idle)
                            return@flow
                        }
                        emit(LocationSearchUiState.Loading)
                        try {
                            val response = geocodingService.searchLocations(query)
                            val results = response.results.orEmpty()
                            if (results.isEmpty()) {
                                emit(LocationSearchUiState.Empty(query))
                            } else {
                                emit(LocationSearchUiState.Results(results))
                            }
                        } catch (e: Exception) {
                            emit(LocationSearchUiState.Error(e.message ?: "Search failed"))
                        }
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectLocation(dto: GeocodingResultDto) {
        viewModelScope.launch {
            val location = OpenMeteoMapper.mapGeocodingResult(dto)
            locationRepository.insert(location)
            _navigateBack.emit(Unit)
        }
    }
}
