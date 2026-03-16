package com.prgramed.eprayer.feature.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.usecase.GetQiblaDirectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    getQiblaDirectionUseCase: GetQiblaDirectionUseCase,
    locationRepository: LocationRepository,
) : ViewModel() {

    val uiState: StateFlow<QiblaUiState> = combine(
        getQiblaDirectionUseCase(),
        locationRepository.getCurrentLocation(),
    ) { direction, location ->
        QiblaUiState(
            qiblaDirection = direction,
            cityName = location.cityName,
            isLoading = false,
        )
    }
        .catch { e -> emit(QiblaUiState(isLoading = false, error = e.message)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QiblaUiState(),
        )
}
