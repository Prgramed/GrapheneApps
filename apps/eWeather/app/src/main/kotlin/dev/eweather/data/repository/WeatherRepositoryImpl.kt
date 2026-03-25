package dev.eweather.data.repository

import dev.eweather.data.api.OpenMeteoAirQualityService
import dev.eweather.data.api.OpenMeteoForecastService
import dev.eweather.data.api.OpenMeteoMapper
import dev.eweather.data.db.dao.WeatherDao
import dev.eweather.data.db.entity.WeatherCacheEntity
import dev.eweather.domain.model.AirQuality
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherData
import dev.eweather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val forecastService: OpenMeteoForecastService,
    private val airQualityService: OpenMeteoAirQualityService,
    private val weatherDao: WeatherDao,
    private val json: Json,
) : WeatherRepository {

    private val cacheTtlMs = 60L * 60 * 1000 // 1 hour

    override fun getWeatherForLocation(location: SavedLocation): Flow<WeatherData?> = flow {
        // 1. Emit cached data immediately
        val cached = weatherDao.getCacheForLocation(location.id, DATA_TYPE_FORECAST)
        if (cached != null) {
            val data = deserializeWeather(cached.json)
            emit(data)

            // If cache is fresh, stop here
            if (System.currentTimeMillis() - cached.fetchedAt < cacheTtlMs) return@flow
        }

        // 2. Fetch fresh from API
        try {
            val dto = forecastService.getForecast(location.lat, location.lon)
            val data = OpenMeteoMapper.mapForecast(dto)
            cacheWeather(location.id, data)
            emit(data)
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch weather for ${location.name}")
            if (cached == null) emit(null)
        }
    }

    override suspend fun refreshWeather(location: SavedLocation): Result<WeatherData> = try {
        val dto = forecastService.getForecast(location.lat, location.lon)
        val data = OpenMeteoMapper.mapForecast(dto)
        cacheWeather(location.id, data)
        Result.success(data)
    } catch (e: Exception) {
        Timber.w(e, "Failed to refresh weather for ${location.name}")
        Result.failure(e)
    }

    override fun getAirQuality(location: SavedLocation): Flow<AirQuality?> = flow {
        // 1. Check cache
        val cached = weatherDao.getCacheForLocation(location.id, DATA_TYPE_AQ)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < cacheTtlMs) {
            emit(deserializeAirQuality(cached.json))
            return@flow
        }

        // 2. Fetch fresh
        try {
            val dto = airQualityService.getAirQuality(location.lat, location.lon)
            val aq = OpenMeteoMapper.mapAirQuality(dto)
            if (aq != null) {
                weatherDao.upsertCache(
                    WeatherCacheEntity(
                        locationId = location.id,
                        dataType = DATA_TYPE_AQ,
                        json = json.encodeToString(aq),
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
            emit(aq)
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch air quality for ${location.name}")
            // Fall back to cached
            if (cached != null) emit(deserializeAirQuality(cached.json))
            else emit(null)
        }
    }

    private suspend fun cacheWeather(locationId: Long, data: WeatherData) {
        weatherDao.upsertCache(
            WeatherCacheEntity(
                locationId = locationId,
                dataType = DATA_TYPE_FORECAST,
                json = json.encodeToString(data),
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun deserializeWeather(jsonStr: String): WeatherData? = try {
        json.decodeFromString<WeatherData>(jsonStr)
    } catch (_: Exception) { null }

    private fun deserializeAirQuality(jsonStr: String): AirQuality? = try {
        json.decodeFromString<AirQuality>(jsonStr)
    } catch (_: Exception) { null }

    companion object {
        private const val DATA_TYPE_FORECAST = "forecast"
        private const val DATA_TYPE_AQ = "air_quality"
    }
}
