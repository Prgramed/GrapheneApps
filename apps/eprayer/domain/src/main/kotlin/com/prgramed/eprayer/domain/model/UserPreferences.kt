package com.prgramed.eprayer.domain.model

data class UserPreferences(
    val calculationMethod: CalculationMethodType = CalculationMethodType.MUSLIM_WORLD_LEAGUE,
    val locationMode: LocationMode = LocationMode.GPS,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualCityName: String? = null,
    val notificationsEnabled: Boolean = true,
    val madhab: MadhabType = MadhabType.SHAFI,
    val adhanSound: AdhanSound = AdhanSound.MOHAMMED_REFAAT,
)

enum class LocationMode {
    GPS,
    MANUAL,
}

enum class MadhabType {
    SHAFI,
    HANAFI,
}

enum class AdhanSound {
    MOHAMMED_REFAAT,
    ABDEL_BASSET,
    AL_HUSARY,
    DEVICE_DEFAULT,
    SILENT,
}
