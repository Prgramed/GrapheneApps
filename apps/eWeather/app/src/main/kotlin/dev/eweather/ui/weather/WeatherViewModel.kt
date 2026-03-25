package dev.eweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.eweather.data.db.dao.WeatherDao
import dev.eweather.data.preferences.AppPreferencesRepository
import dev.eweather.domain.model.AirQuality
import dev.eweather.domain.model.MoonPhaseInfo
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherAlert
import dev.eweather.domain.model.WeatherData
import dev.eweather.domain.repository.AlertRepository
import dev.eweather.domain.repository.LocationRepository
import dev.eweather.domain.repository.WeatherRepository
import dev.eweather.location.LocationProvider
import dev.eweather.location.LocationResult
import dev.eweather.ui.weather.components.deriveSkyCategory
import dev.eweather.util.MoonPhaseCalculator
import dev.eweather.util.SkyCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data object NoLocation : WeatherUiState()
    data class Success(
        val weatherData: WeatherData,
        val airQuality: AirQuality?,
        val alerts: List<WeatherAlert>,
        val location: SavedLocation,
        val skyCategory: SkyCategory,
        val animationIntensity: Float,
        val moonPhase: MoonPhaseInfo,
        val fetchedAt: Long = System.currentTimeMillis(),
    ) : WeatherUiState()
    data class Error(val message: String, val cachedData: WeatherData?) : WeatherUiState()
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val alertRepository: AlertRepository,
    private val locationProvider: LocationProvider,
    private val preferencesRepository: AppPreferencesRepository,
    private val weatherDao: WeatherDao,
) : ViewModel() {

    // Top-level state (used only when no locations exist yet)
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // Multi-location carousel state
    private val _locations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val locations: StateFlow<List<SavedLocation>> = _locations.asStateFlow()

    private val _pageStates = MutableStateFlow<Map<Long, WeatherUiState>>(emptyMap())
    val pageStates: StateFlow<Map<Long, WeatherUiState>> = _pageStates.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val loadJobs = mutableMapOf<Long, Job>()

    init {
        observeLocations()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) ensureLocation()
        else _uiState.value = WeatherUiState.NoLocation
    }

    fun refresh() {
        val locs = _locations.value
        val idx = _currentPageIndex.value
        if (idx !in locs.indices) return
        val location = locs[idx]

        loadJobs[location.id]?.cancel()
        loadJobs[location.id] = viewModelScope.launch(Dispatchers.IO) {
            weatherRepository.refreshWeather(location)
            loadWeatherForPage(location)
        }
    }

    fun onPageSettled(index: Int) {
        val locs = _locations.value
        if (index !in locs.indices) return
        val location = locs[index]
        _currentPageIndex.value = index

        viewModelScope.launch {
            preferencesRepository.updateActiveLocationId(location.id)
        }

        // Load weather if not yet loaded or errored
        val state = _pageStates.value[location.id]
        if (state == null || state is WeatherUiState.Error) {
            loadJobs[location.id]?.cancel()
            loadJobs[location.id] = viewModelScope.launch(Dispatchers.IO) {
                loadWeatherForPage(location)
            }
        }
    }

    private fun ensureLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = WeatherUiState.Loading
            val locs = locationRepository.observeAll().first()
            if (locs.isEmpty()) {
                val location = resolveLocation()
                if (location == null) {
                    _uiState.value = WeatherUiState.NoLocation
                } else {
                    preferencesRepository.updateActiveLocationId(location.id)
                    // observeLocations() will pick it up and load weather
                }
            }
            // If locations already exist, observeLocations() handles everything
        }
    }

    private fun observeLocations() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                locationRepository.observeAll(),
                preferencesRepository.preferencesFlow,
            ) { locs, prefs -> locs to prefs.activeLocationId }
                .collect { (locs, activeId) ->
                    _locations.value = locs

                    if (locs.isEmpty()) return@collect

                    // Sync page index with activeLocationId
                    val idx = locs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
                    _currentPageIndex.value = idx

                    val currentIds = locs.map { it.id }.toSet()

                    // Cancel jobs for removed locations
                    loadJobs.keys.filter { it !in currentIds }.forEach { id ->
                        loadJobs.remove(id)?.cancel()
                    }

                    // Start loading for new locations
                    for (loc in locs) {
                        if (loc.id !in loadJobs) {
                            loadJobs[loc.id] = viewModelScope.launch(Dispatchers.IO) {
                                loadWeatherForPage(loc)
                            }
                        }
                    }

                    // Clean up stale pageStates
                    _pageStates.value = _pageStates.value.filterKeys { it in currentIds }
                }
        }
    }

    private suspend fun resolveLocation(): SavedLocation? {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.activeLocationId > 0) {
            val stored = locationRepository.getById(prefs.activeLocationId)
            if (stored != null) return stored
        }

        val lastKnown = locationProvider.getLastKnownFromStore()
        if (lastKnown != null) {
            return locationRepository.getOrCreateGpsLocation(lastKnown.first, lastKnown.second)
        }

        return when (val result = locationProvider.requestCurrentLocation()) {
            is LocationResult.Success ->
                locationRepository.getOrCreateGpsLocation(result.lat, result.lon)
            else -> null
        }
    }

    private suspend fun loadWeatherForPage(location: SavedLocation) {
        _pageStates.value = _pageStates.value + (location.id to WeatherUiState.Loading)

        try {
            weatherRepository.getWeatherForLocation(location)
                .catch { e ->
                    _pageStates.value = _pageStates.value +
                        (location.id to WeatherUiState.Error(e.message ?: "Error", null))
                }
                .collect { weatherData ->
                    if (weatherData == null) {
                        _pageStates.value = _pageStates.value +
                            (location.id to WeatherUiState.Error("No weather data", null))
                        return@collect
                    }

                    val hour = LocalTime.now().hour
                    val current = weatherData.current
                    val skyCategory = deriveSkyCategory(current.weatherCode, current.isDay, hour)
                    val intensity = deriveAnimationIntensity(current.precipitation, current.windSpeed)
                    val today = LocalDate.now()
                    val moonPhase = MoonPhaseCalculator.calculate(today.year, today.monthValue, today.dayOfMonth)

                    val airQuality = try {
                        weatherRepository.getAirQuality(location).first()
                    } catch (_: Exception) { null }

                    val alerts = try {
                        alertRepository.refreshAlerts(location)
                    } catch (_: Exception) { emptyList() }

                    val cachedAt = try {
                        weatherDao.getCacheForLocation(location.id, "forecast")?.fetchedAt
                            ?: System.currentTimeMillis()
                    } catch (_: Exception) { System.currentTimeMillis() }

                    _pageStates.value = _pageStates.value + (location.id to WeatherUiState.Success(
                        weatherData = weatherData,
                        airQuality = airQuality,
                        alerts = alerts,
                        location = location,
                        skyCategory = skyCategory,
                        animationIntensity = intensity,
                        moonPhase = moonPhase,
                        fetchedAt = cachedAt,
                    ))
                }
        } catch (e: Exception) {
            _pageStates.value = _pageStates.value +
                (location.id to WeatherUiState.Error(e.message ?: "Failed to load weather", null))
        }
    }

    private fun deriveAnimationIntensity(precipitation: Float, windSpeed: Float): Float {
        val precipIntensity = (precipitation / 10f).coerceIn(0f, 1f)
        val windIntensity = (windSpeed / 50f).coerceIn(0f, 1f)
        return maxOf(precipIntensity, windIntensity * 0.5f)
    }
}
