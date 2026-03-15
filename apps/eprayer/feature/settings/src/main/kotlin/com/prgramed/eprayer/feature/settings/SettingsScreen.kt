package com.prgramed.eprayer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.feature.settings.components.CalculationMethodSection
import com.prgramed.eprayer.feature.settings.components.CitySearchDialog
import com.prgramed.eprayer.feature.settings.components.LocationSettingsSection
import com.prgramed.eprayer.feature.settings.components.NotificationSettingsSection

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCitySearch by remember { mutableStateOf(false) }

    if (showCitySearch) {
        CitySearchDialog(
            onDismiss = { showCitySearch = false },
            onCitySelected = { city ->
                viewModel.updateManualLocation(
                    city.latitude.toString(),
                    city.longitude.toString(),
                    city.displayName,
                )
                showCitySearch = false
            },
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            LocationSettingsSection(
                locationMode = uiState.locationMode,
                selectedCity = uiState.manualCityName,
                hasLocationPermission = uiState.hasLocationPermission,
                onLocationModeChanged = viewModel::updateLocationMode,
                onManualLocationClicked = { showCitySearch = true },
                onLocationPermissionResult = viewModel::updateLocationPermission,
            )
        }

        item { HorizontalDivider() }

        item {
            CalculationMethodSection(
                selectedMethod = uiState.calculationMethod,
                onMethodSelected = viewModel::updateCalculationMethod,
            )
        }

        item { HorizontalDivider() }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Madhab (Asr Calculation)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MadhabType.entries.forEach { madhab ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.madhab == madhab,
                            onClick = { viewModel.updateMadhab(madhab) },
                        )
                        Text(
                            text = madhab.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        item { HorizontalDivider() }

        item {
            NotificationSettingsSection(
                notificationsEnabled = uiState.notificationsEnabled,
                hasNotificationPermission = uiState.hasNotificationPermission,
                onNotificationsEnabledChanged = viewModel::updateNotificationsEnabled,
                onNotificationPermissionResult = viewModel::updateNotificationPermission,
            )
        }
    }
}
