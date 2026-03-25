package dev.eweather.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQualityResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hourly: AirQualityHourlyDto? = null,
)

@Serializable
data class AirQualityHourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("pm2_5") val pm25: List<Float?> = emptyList(),
    val pm10: List<Float?> = emptyList(),
    @SerialName("nitrogen_dioxide") val no2: List<Float?> = emptyList(),
    val ozone: List<Float?> = emptyList(),
    @SerialName("european_aqi") val europeanAqi: List<Int?> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Int?> = emptyList(),
)
