package com.prgramed.eprayer.feature.prayertimes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.usecase.GetPrayerTimesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(
    private val getPrayerTimesUseCase: GetPrayerTimesUseCase,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        startCountdown()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && observeJob == null) {
            startObserving()
            observeCity()
        } else if (!granted) {
            _uiState.update {
                it.copy(isLoading = false, error = "Location permission required to show prayer times")
            }
        }
    }

    private fun startObserving() {
        observeJob = viewModelScope.launch {
            val javaToday = java.time.LocalDate.now()
            val today = kotlinx.datetime.LocalDate(
                javaToday.year, javaToday.monthValue, javaToday.dayOfMonth,
            )
            getPrayerTimesUseCase(today)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { prayerDay ->
                    _uiState.update {
                        it.copy(
                            prayerDay = prayerDay,
                            nextPrayer = prayerDay.nextPrayer,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    private fun observeCity() {
        viewModelScope.launch {
            locationRepository.getCurrentLocation()
                .catch { /* ignore */ }
                .collect { location ->
                    _uiState.update { it.copy(cityName = location.cityName) }
                }
        }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (true) {
                val next = _uiState.value.nextPrayer
                if (next != null) {
                    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    val remaining = next.time - now
                    _uiState.update {
                        it.copy(timeRemaining = if (remaining.isPositive()) remaining else null)
                    }
                }
                delay(1.seconds)
            }
        }
    }
}
