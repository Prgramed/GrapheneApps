package dev.eweather.domain.repository

import dev.eweather.domain.model.AirQuality
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherData
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun getWeatherForLocation(location: SavedLocation): Flow<WeatherData?>
    suspend fun refreshWeather(location: SavedLocation): Result<WeatherData>
    fun getAirQuality(location: SavedLocation): Flow<AirQuality?>
}
