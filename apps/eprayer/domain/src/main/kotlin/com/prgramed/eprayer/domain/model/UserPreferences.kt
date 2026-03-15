package com.prgramed.eprayer.domain.model

data class UserPreferences(
    val calculationMethod: CalculationMethodType = CalculationMethodType.MUSLIM_WORLD_LEAGUE,
    val locationMode: LocationMode = LocationMode.GPS,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualCityName: String? = null,
    val notificationsEnabled: Boolean = true,
    val madhab: MadhabType = MadhabType.SHAFI,
)

enum class LocationMode {
    GPS,
    MANUAL,
}

enum class MadhabType {
    SHAFI,
    HANAFI,
}
