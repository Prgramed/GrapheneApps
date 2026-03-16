package com.prgramed.eprayer.feature.settings

import com.prgramed.eprayer.domain.model.AdhanSound
import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.model.MadhabType

data class SettingsUiState(
    val calculationMethod: CalculationMethodType = CalculationMethodType.MUSLIM_WORLD_LEAGUE,
    val locationMode: LocationMode = LocationMode.GPS,
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val manualCityName: String = "",
    val madhab: MadhabType = MadhabType.SHAFI,
    val adhanSound: AdhanSound = AdhanSound.MOHAMMED_REFAAT,
    val notificationsEnabled: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isLoading: Boolean = true,
)
