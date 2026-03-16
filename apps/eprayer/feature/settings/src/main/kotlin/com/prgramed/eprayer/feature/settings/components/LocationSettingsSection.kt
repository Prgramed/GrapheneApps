package com.prgramed.eprayer.feature.settings.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prgramed.eprayer.domain.model.LocationMode

private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)

@Composable
fun LocationSettingsSection(
    locationMode: LocationMode,
    selectedCity: String,
    hasLocationPermission: Boolean,
    onLocationModeChanged: (LocationMode) -> Unit,
    onManualLocationClicked: () -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.any { it }
        onLocationPermissionResult(granted)
        if (granted) onLocationModeChanged(LocationMode.GPS)
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Location",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = locationMode == LocationMode.GPS,
                onClick = {
                    if (hasLocationPermission) {
                        onLocationModeChanged(LocationMode.GPS)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Peach,
                    unselectedColor = TextMuted,
                ),
            )
            Text("GPS", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = locationMode == LocationMode.MANUAL,
                onClick = { onLocationModeChanged(LocationMode.MANUAL) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Peach,
                    unselectedColor = TextMuted,
                ),
            )
            Text("Select City", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }

        if (locationMode == LocationMode.MANUAL) {
            if (selectedCity.isNotBlank()) {
                Text(
                    text = selectedCity,
                    fontSize = 14.sp,
                    color = Peach,
                    modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                )
            }
            Button(
                onClick = onManualLocationClicked,
                modifier = Modifier.padding(start = 40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Peach,
                    contentColor = Color(0xFF0F1B2D),
                ),
            ) {
                Text(if (selectedCity.isBlank()) "Choose City" else "Change City")
            }
        }
    }
}
