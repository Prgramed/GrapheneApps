package dev.eweather.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RainViewerResponseDto(
    val host: String? = null,
    val radar: RadarDto? = null,
)

@Serializable
data class RadarDto(
    val past: List<RadarFrameDto> = emptyList(),
    val nowcast: List<RadarFrameDto> = emptyList(),
)

@Serializable
data class RadarFrameDto(
    val time: Long? = null,
    val path: String? = null,
)
