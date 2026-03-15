package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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

@Composable
fun PrayerTimeCard(
    prayerTime: PrayerTime,
    modifier: Modifier = Modifier,
) {
    val displayName = prayerTime.prayer.name.lowercase().replaceFirstChar { it.uppercase() }
    val formattedTime = formatTime(prayerTime)

    val containerColor = if (prayerTime.isNext) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (prayerTime.isNext) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (prayerTime.isNext) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun formatTime(prayerTime: PrayerTime): String {
    val instant = java.time.Instant.ofEpochMilli(prayerTime.time.toEpochMilliseconds())
    val zonedTime = instant.atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("hh:mm a").format(zonedTime)
}
