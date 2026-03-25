package dev.eweather.domain.model

data class RadarFrame(
    val timestamp: Long,
    val tileUrlTemplate: String, // e.g. "/v2/radar/{timestamp}/512/{z}/{x}/{y}/6/1_1.png"
)
