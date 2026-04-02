package dev.eweather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.eweather.data.api.MeteoAlarmCountries
import dev.eweather.data.preferences.AppPreferencesRepository
import dev.eweather.domain.model.WeatherAlert
import dev.eweather.domain.repository.AlertRepository
import dev.eweather.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<WeatherAlert>>(emptyList())
    val alerts: StateFlow<List<WeatherAlert>> = _alerts.asStateFlow()

    private val _isEurope = MutableStateFlow(true)
    val isEurope: StateFlow<Boolean> = _isEurope.asStateFlow()

    private val _locationName = MutableStateFlow("")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.preferencesFlow.first()
            val location = if (prefs.activeLocationId > 0) {
                locationRepository.getById(prefs.activeLocationId)
            } else null

            if (location != null) {
                _locationName.value = location.name
                _isEurope.value = MeteoAlarmCountries.isInEurope(location.lat, location.lon)

                alertRepository.observeActiveAlerts(location.id)
                    .map { alerts -> alerts.sortedByDescending { it.severity.ordinal } }
                    .catch { timber.log.Timber.w(it, "Alert observation failed") }
                    .collect { _alerts.value = it }
            }
        }
    }
}
