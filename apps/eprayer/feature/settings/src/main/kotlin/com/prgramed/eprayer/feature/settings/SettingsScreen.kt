package com.prgramed.eprayer.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.feature.settings.components.AdhanSoundSection
import com.prgramed.eprayer.feature.settings.components.CalculationMethodSection
import com.prgramed.eprayer.feature.settings.components.CitySearchDialog
import com.prgramed.eprayer.feature.settings.components.LocationSettingsSection
import com.prgramed.eprayer.feature.settings.components.NotificationSettingsSection

private val Navy = Color(0xFF0F1B2D)
private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)
private val DividerColor = Color(0xFF1E2F47)

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Navy),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

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

        item { HorizontalDivider(color = DividerColor) }

        item {
            CalculationMethodSection(
                selectedMethod = uiState.calculationMethod,
                onMethodSelected = viewModel::updateCalculationMethod,
            )
        }

        item { HorizontalDivider(color = DividerColor) }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Madhab (Asr Calculation)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MadhabType.entries.forEach { madhab ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.madhab == madhab,
                            onClick = { viewModel.updateMadhab(madhab) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Peach,
                                unselectedColor = TextMuted,
                            ),
                        )
                        Text(
                            text = madhab.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        item { HorizontalDivider(color = DividerColor) }

        item {
            AdhanSoundSection(
                selectedSound = uiState.adhanSound,
                onSoundSelected = viewModel::updateAdhanSound,
            )
        }

        item { HorizontalDivider(color = DividerColor) }

        item {
            NotificationSettingsSection(
                notificationsEnabled = uiState.notificationsEnabled,
                hasNotificationPermission = uiState.hasNotificationPermission,
                onNotificationsEnabledChanged = viewModel::updateNotificationsEnabled,
                onNotificationPermissionResult = viewModel::updateNotificationPermission,
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
