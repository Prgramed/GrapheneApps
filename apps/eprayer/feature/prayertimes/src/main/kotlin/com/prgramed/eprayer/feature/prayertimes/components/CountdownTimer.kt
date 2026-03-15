package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.time.Duration

@Composable
fun CountdownTimer(
    duration: Duration,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formatted = "%02d:%02d:%02d".format(hours, minutes, seconds)

    Text(
        text = formatted,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
