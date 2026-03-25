package dev.eweather.domain.model

@kotlinx.serialization.Serializable
data class WeatherData(
    val current: CurrentWeather,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val airQuality: AirQuality? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)
