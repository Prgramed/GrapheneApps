package com.prgramed.eprayer.feature.prayertimes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
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
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerTimesUiState())
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var countdownJob: Job? = null
    private var lastLoadedDate: java.time.LocalDate? = null

    init {
        startCountdown()
        observePreferences()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && observeJob == null) {
            startObserving()
            observeCity()
        } else if (!granted) {
            _uiState.update {
                it.copy(isLoading = false, error = "Location permission required")
            }
        }
    }

    fun togglePrayerNotification(prayer: String) {
        viewModelScope.launch {
            val current = _uiState.value.enabledNotifications
            val enabled = prayer !in current
            userPreferencesRepository.updatePrayerNotificationEnabled(prayer, enabled)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.getUserPreferences()
                .catch { }
                .collect { prefs ->
                    _uiState.update { it.copy(enabledNotifications = prefs.enabledPrayerNotifications) }
                }
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        val javaToday = java.time.LocalDate.now()
        lastLoadedDate = javaToday
        val today = kotlinx.datetime.LocalDate(
            javaToday.year, javaToday.monthValue, javaToday.dayOfMonth,
        )
        val hijri = com.prgramed.eprayer.domain.model.HijriDate.fromGregorian(
            javaToday.year, javaToday.monthValue, javaToday.dayOfMonth,
        )
        observeJob = viewModelScope.launch {
            getPrayerTimesUseCase(today)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { prayerDay ->
                    _uiState.update {
                        it.copy(
                            prayerDay = prayerDay,
                            nextPrayer = prayerDay.nextPrayer,
                            hijriDate = hijri.formatted,
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
                .catch { }
                .collect { location ->
                    _uiState.update { it.copy(cityName = location.cityName) }
                }
        }
    }

    private fun startCountdown() {
        countdownJob = viewModelScope.launch {
            while (true) {
                // Check if date changed (midnight boundary)
                val currentDate = java.time.LocalDate.now()
                if (lastLoadedDate != null && currentDate != lastLoadedDate) {
                    // Day changed — reload prayer times for new day
                    startObserving()
                }

                val next = _uiState.value.nextPrayer
                if (next != null) {
                    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    val remaining = next.time - now
                    if (remaining.isPositive()) {
                        _uiState.update { it.copy(timeRemaining = remaining) }
                    } else {
                        // Current next prayer has passed — recalculate
                        _uiState.update { it.copy(timeRemaining = null) }
                        val prayerDay = _uiState.value.prayerDay
                        if (prayerDay != null) {
                            val nowMillis = System.currentTimeMillis()
                            val newNext = prayerDay.times.firstOrNull {
                                it.time.toEpochMilliseconds() > nowMillis
                            }
                            _uiState.update { it.copy(nextPrayer = newNext) }
                        }
                    }
                }
                delay(60.seconds)
            }
        }
    }
}
