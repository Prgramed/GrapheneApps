package com.prgramed.eprayer.feature.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.usecase.GetQiblaDirectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val getQiblaDirectionUseCase: GetQiblaDirectionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    init {
        observeQibla()
    }

    private fun observeQibla() {
        viewModelScope.launch {
            getQiblaDirectionUseCase()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { direction ->
                    _uiState.update {
                        it.copy(
                            qiblaDirection = direction,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }
}
