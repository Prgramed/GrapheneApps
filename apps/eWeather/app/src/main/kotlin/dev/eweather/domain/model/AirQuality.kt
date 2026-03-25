package dev.eweather.domain.model

@kotlinx.serialization.Serializable
data class AirQuality(
    val pm25: Float?,
    val pm10: Float?,
    val no2: Float?,
    val ozone: Float?,
    val europeanAqi: Int?,
    val usAqi: Int?,
)
