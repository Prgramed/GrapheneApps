package dev.eweather.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val elevation: Double? = null,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
)

@Serializable
data class CurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Float? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Float? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Float? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    val precipitation: Float? = null,
    @SerialName("surface_pressure") val surfacePressure: Float? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    val visibility: Float? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
data class HourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Float?> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Float?> = emptyList(),
    @SerialName("precipitation_probability") val precipProbability: List<Int?> = emptyList(),
    val precipitation: List<Float?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Float?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Float?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Float?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Float?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Float?> = emptyList(),
    @SerialName("precipitation_sum") val precipSum: List<Float?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Float?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirDominant: List<Int?> = emptyList(),
)
