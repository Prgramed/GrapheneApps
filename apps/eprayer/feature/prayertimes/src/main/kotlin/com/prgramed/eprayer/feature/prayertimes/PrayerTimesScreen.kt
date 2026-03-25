package com.prgramed.eprayer.feature.prayertimes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.feature.prayertimes.components.NextPrayerBanner
import com.prgramed.eprayer.feature.prayertimes.components.PrayerTimeCard

private val Navy = Color(0xFF0F1B2D)
private val Peach = Color(0xFFE8B98A)

@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Navy),
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Peach)
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.error ?: "Error", color = Peach)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.onPermissionResult(true) }) {
                        Text("Retry", color = Peach)
                    }
                }
            }
            else -> {
                val prayerDay = uiState.prayerDay ?: return
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val nextPrayer = uiState.nextPrayer
                    if (nextPrayer != null) {
                        item {
                            NextPrayerBanner(
                                prayerTime = nextPrayer,
                                timeRemaining = uiState.timeRemaining,
                                cityName = uiState.cityName,
                                hijriDate = uiState.hijriDate,
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(prayerDay.times) { prayerTime ->
                        PrayerTimeCard(
                            prayerTime = prayerTime,
                            notificationEnabled = prayerTime.prayer.name in uiState.enabledNotifications,
                            onNotificationToggle = {
                                viewModel.togglePrayerNotification(prayerTime.prayer.name)
                            },
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
