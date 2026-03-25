package dev.eweather.domain.model

data class AstronomyData(
    val sunrise: String, // ISO datetime
    val sunset: String,
    val dayLengthMinutes: Int,
    val moonPhase: MoonPhaseInfo,
)

data class MoonPhaseInfo(
    val fraction: Float, // 0.0 to 1.0 through the synodic cycle
    val illumination: Float, // 0.0 to 1.0
    val phaseName: String, // "Waxing Crescent", "Full Moon", etc.
    val emoji: String, // 🌑🌒🌓🌔🌕🌖🌗🌘
)
