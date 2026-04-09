package com.prgramed.eprayer.feature.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.usecase.GetQiblaDirectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val getQiblaDirectionUseCase: GetQiblaDirectionUseCase,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<QiblaUiState> = retryTrigger
        .flatMapLatest {
            combine(
                getQiblaDirectionUseCase(),
                locationRepository.getCurrentLocation(),
            ) { direction, location ->
                QiblaUiState(
                    qiblaDirection = direction,
                    cityName = location.cityName,
                    isLoading = false,
                    needsCalibration = direction.needsCalibration,
                )
            }.catch { e -> emit(QiblaUiState(isLoading = false, error = e.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QiblaUiState(),
        )

    fun retry() {
        retryTrigger.value++
    }
}
