package com.prgramed.eprayer.feature.prayertimes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.usecase.GetPrayerTimesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(
    private val getPrayerTimesUseCase: GetPrayerTimesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()

    init {
        observePrayerTimes()
        startCountdown()
    }

    private fun observePrayerTimes() {
        viewModelScope.launch {
            val now = kotlinx.datetime.Instant.fromEpochMilliseconds(
                java.lang.System.currentTimeMillis(),
            )
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
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

    private fun startCountdown() {
        viewModelScope.launch {
            while (true) {
                val next = _uiState.value.nextPrayer
                if (next != null) {
                    val now = Instant.fromEpochMilliseconds(
                        java.lang.System.currentTimeMillis(),
                    )
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
