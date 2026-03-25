package dev.eweather.domain.model

@kotlinx.serialization.Serializable
data class DailyPoint(
    val date: String, // ISO date: "2026-03-23"
    val tempMax: Float,
    val tempMin: Float,
    val weatherCode: Int,
    val sunrise: String, // ISO datetime: "2026-03-23T05:40"
    val sunset: String,
    val uvIndexMax: Float,
    val precipSum: Float,
    val windSpeedMax: Float,
    val windDirDominant: Int,
)
