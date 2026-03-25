package dev.eweather.domain.model

@kotlinx.serialization.Serializable
data class HourlyPoint(
    val timestamp: Long,
    val temp: Float,
    val feelsLike: Float,
    val weatherCode: Int,
    val precipProbability: Int,
    val precipAmount: Float,
    val windSpeed: Float,
    val windDirection: Int,
    val humidity: Int,
    val uvIndex: Float,
    val isDay: Boolean,
)
