package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapheneapps.core.designsystem.theme.DarkGreen
import com.grapheneapps.core.designsystem.theme.MediumGreen
import com.grapheneapps.core.designsystem.theme.Seed
import com.prgramed.eprayer.domain.model.PrayerTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

@Composable
fun NextPrayerBanner(
    prayerTime: PrayerTime,
    timeRemaining: Duration?,
    cityName: String? = null,
    modifier: Modifier = Modifier,
) {
    val displayName = prayerTime.prayer.name.lowercase().replaceFirstChar { it.uppercase() }
    val formattedTime = formatPrayerTime(prayerTime)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(colors = listOf(Seed, DarkGreen, MediumGreen)),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (cityName != null) "Next Prayer \u2022 $cityName" else "Next Prayer",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
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
