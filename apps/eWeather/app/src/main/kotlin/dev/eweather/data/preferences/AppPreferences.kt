package dev.eweather.data.preferences

enum class TemperatureUnit(val label: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
}

enum class WindUnit(val label: String) {
    MS("m/s"),
    KMH("km/h"),
    MPH("mph"),
}

data class AppPreferences(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val refreshIntervalHours: Int = 1,
    val notificationsEnabled: Boolean = true,
    val activeLocationId: Long = 0,
)
