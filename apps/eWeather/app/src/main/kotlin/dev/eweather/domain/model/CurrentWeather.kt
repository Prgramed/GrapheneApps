package dev.eweather.domain.model

@kotlinx.serialization.Serializable
data class CurrentWeather(
    val temp: Float,
    val feelsLike: Float,
    val weatherCode: Int,
    val windSpeed: Float,
    val windDirection: Int,
    val humidity: Int,
    val pressure: Float,
    val cloudCover: Int,
    val visibility: Float,
    val precipitation: Float,
    val isDay: Boolean,
    val timestamp: Long,
)
