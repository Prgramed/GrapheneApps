package dev.eweather.domain.model

data class WeatherAlert(
    val id: String,
    val event: String,
    val severity: AlertSeverity,
    val headline: String,
    val area: String,
    val effective: Long,
    val expires: Long,
)

enum class AlertSeverity {
    MINOR,
    MODERATE,
    SEVERE,
    EXTREME,
}
