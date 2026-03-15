package com.prgramed.eprayer.feature.prayertimes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.feature.prayertimes.components.DayProgressIndicator
import com.prgramed.eprayer.feature.prayertimes.components.NextPrayerBanner
import com.prgramed.eprayer.feature.prayertimes.components.PrayerTimeCard

@Composable
fun PrayerTimesScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            val prayerDay = uiState.prayerDay ?: return
            LazyColumn(modifier = modifier.fillMaxSize()) {
                val nextPrayer = uiState.nextPrayer
                if (nextPrayer != null) {
                    item {
                        NextPrayerBanner(
                            prayerTime = nextPrayer,
                            timeRemaining = uiState.timeRemaining,
                        )
                    }
                }

                item {
                    DayProgressIndicator(prayerDay = prayerDay)
                }

                items(prayerDay.times) { prayerTime ->
                    PrayerTimeCard(prayerTime = prayerTime)
                }
            }
        }
    }
}
