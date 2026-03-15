package com.prgramed.eprayer.feature.settings.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prgramed.eprayer.domain.model.LocationMode

@Composable
fun LocationSettingsSection(
    locationMode: LocationMode,
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
            style = MaterialTheme.typography.titleMedium,
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
            )
            Text("GPS", modifier = Modifier.padding(start = 8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = locationMode == LocationMode.MANUAL,
                onClick = { onLocationModeChanged(LocationMode.MANUAL) },
            )
            Text("Manual", modifier = Modifier.padding(start = 8.dp))
        }

        if (locationMode == LocationMode.MANUAL) {
            Button(
                onClick = onManualLocationClicked,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Set Coordinates")
            }
        }
    }
}
