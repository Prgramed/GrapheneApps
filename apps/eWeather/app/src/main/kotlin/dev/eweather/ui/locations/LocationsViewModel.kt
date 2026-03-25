package dev.eweather.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.eweather.data.db.dao.WeatherDao
import dev.eweather.data.preferences.AppPreferencesRepository
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherData
import dev.eweather.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class LocationWithTemp(
    val location: SavedLocation,
    val cachedTemp: Float? = null,
    val isActive: Boolean = false,
)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val weatherDao: WeatherDao,
    private val preferencesRepository: AppPreferencesRepository,
    private val json: Json,
) : ViewModel() {

    private val _locations = MutableStateFlow<List<LocationWithTemp>>(emptyList())
    val locations: StateFlow<List<LocationWithTemp>> = _locations.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                locationRepository.observeAll(),
                preferencesRepository.preferencesFlow,
            ) { locations, prefs ->
                locations.map { loc ->
                    val temp = getCachedTemp(loc.id)
                    LocationWithTemp(
                        location = loc,
                        cachedTemp = temp,
                        isActive = loc.id == prefs.activeLocationId,
                    )
                }
            }.collect { _locations.value = it }
        }
    }

    fun setActive(locationId: Long) {
        viewModelScope.launch {
            preferencesRepository.updateActiveLocationId(locationId)
        }
    }

    fun delete(location: SavedLocation) {
        viewModelScope.launch {
            locationRepository.delete(location)
        }
    }

    private suspend fun getCachedTemp(locationId: Long): Float? {
        val cache = weatherDao.getCacheForLocation(locationId, "forecast") ?: return null
        return try {
            val data = json.decodeFromString<WeatherData>(cache.json)
            data.current.temp
        } catch (_: Exception) { null }
    }
}
