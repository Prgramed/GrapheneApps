package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prgramed.eprayer.domain.model.PrayerTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

@Composable
fun NextPrayerBanner(
    prayerTime: PrayerTime,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
) {
    val displayName = prayerTime.prayer.name.lowercase().replaceFirstChar { it.uppercase() }
    val formattedTime = formatPrayerTime(prayerTime)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Next Prayer",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (timeRemaining != null) {
                CountdownTimer(duration = timeRemaining)
            }
        }
    }
}

private fun formatPrayerTime(prayerTime: PrayerTime): String {
    val instant = java.time.Instant.ofEpochMilli(prayerTime.time.toEpochMilliseconds())
    val zonedTime = instant.atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("hh:mm a").format(zonedTime)
}
