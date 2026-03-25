package dev.eweather.domain.repository

import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherAlert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    suspend fun refreshAlerts(location: SavedLocation): List<WeatherAlert>
    fun observeActiveAlerts(locationId: Long): Flow<List<WeatherAlert>>
}
