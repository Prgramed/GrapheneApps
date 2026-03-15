package com.prgramed.eprayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import com.prgramed.eprayer.domain.usecase.SchedulePrayerNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val schedulePrayerNotificationsUseCase: SchedulePrayerNotificationsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.getUserPreferences().collect { prefs ->
                _uiState.update {
                    it.copy(
                        calculationMethod = prefs.calculationMethod,
                        locationMode = prefs.locationMode,
                        manualLatitude = prefs.manualLatitude?.toString() ?: "",
                        manualLongitude = prefs.manualLongitude?.toString() ?: "",
                        manualCityName = prefs.manualCityName ?: "",
                        madhab = prefs.madhab,
                        notificationsEnabled = prefs.notificationsEnabled,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun updateCalculationMethod(method: CalculationMethodType) {
        viewModelScope.launch {
            userPreferencesRepository.updateCalculationMethod(method)
            rescheduleNotifications()
        }
    }

    fun updateLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            userPreferencesRepository.updateLocationMode(mode)
        }
    }

    fun updateManualLocation(latitude: String, longitude: String, cityName: String) {
        val lat = latitude.toDoubleOrNull() ?: return
        val lon = longitude.toDoubleOrNull() ?: return
        viewModelScope.launch {
            userPreferencesRepository.updateManualLocation(lat, lon, cityName.ifBlank { null })
            rescheduleNotifications()
        }
    }

    fun updateMadhab(madhab: MadhabType) {
        viewModelScope.launch {
            userPreferencesRepository.updateMadhab(madhab)
            rescheduleNotifications()
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateNotificationsEnabled(enabled)
            if (enabled) rescheduleNotifications()
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun updateNotificationPermission(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
    }

    private suspend fun rescheduleNotifications() {
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(
            java.lang.System.currentTimeMillis(),
        )
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        schedulePrayerNotificationsUseCase(today)
    }
}
