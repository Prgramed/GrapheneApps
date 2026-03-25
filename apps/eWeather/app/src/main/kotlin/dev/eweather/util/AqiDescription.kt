package dev.eweather.util

import androidx.compose.ui.graphics.Color

data class AqiLevel(
    val label: String,
    val color: Color,
    val description: String,
)

object AqiDescription {

    fun europeanAqiLevel(aqi: Int): AqiLevel = when {
        aqi <= 20 -> AqiLevel(
            label = "Good",
            color = Color(0xFF4CAF50),
            description = "Air quality is satisfactory",
        )
        aqi <= 40 -> AqiLevel(
            label = "Fair",
            color = Color(0xFF8BC34A),
            description = "Acceptable for most",
        )
        aqi <= 60 -> AqiLevel(
            label = "Moderate",
            color = Color(0xFFFFEB3B),
            description = "Sensitive groups may be affected",
        )
        aqi <= 80 -> AqiLevel(
            label = "Poor",
            color = Color(0xFFFF9800),
            description = "Everyone may begin to experience effects",
        )
        aqi <= 100 -> AqiLevel(
            label = "Very Poor",
            color = Color(0xFFF44336),
            description = "Health warnings, limit outdoor activity",
        )
        else -> AqiLevel(
            label = "Extremely Poor",
            color = Color(0xFF9C27B0),
            description = "Health alert, avoid outdoor activity",
        )
    }
}
