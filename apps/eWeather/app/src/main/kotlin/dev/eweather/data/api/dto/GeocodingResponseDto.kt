package dev.eweather.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto>? = null,
)

@Serializable
data class GeocodingResultDto(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    val country: String? = null,
    @kotlinx.serialization.SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null, // Region/state
    val timezone: String? = null,
    val population: Long? = null,
)
