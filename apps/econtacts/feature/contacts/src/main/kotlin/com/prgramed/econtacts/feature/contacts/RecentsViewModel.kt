package com.prgramed.econtacts.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.model.RecentCall
import com.prgramed.econtacts.domain.usecase.GetRecentCallsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentsUiState(
    val calls: List<RecentCall> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val getRecentCallsUseCase: GetRecentCallsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentsUiState())
    val uiState: StateFlow<RecentsUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startObserving()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Call log permission required") }
        }
    }

    private var observeJob: Job? = null

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            getRecentCallsUseCase()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { calls ->
                    _uiState.update { it.copy(calls = calls, isLoading = false) }
                }
        }
    }
}
