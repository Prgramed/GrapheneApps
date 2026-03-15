package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prgramed.eprayer.domain.model.PrayerDay

@Composable
fun DayProgressIndicator(
    prayerDay: PrayerDay,
    modifier: Modifier = Modifier,
) {
    val times = prayerDay.times
    if (times.size < 2) return

    val fajrMillis = times.first().time.toEpochMilliseconds().toFloat()
    val ishaMillis = times.last().time.toEpochMilliseconds().toFloat()
    val nowMillis = System.currentTimeMillis().toFloat()

    val progress = ((nowMillis - fajrMillis) / (ishaMillis - fajrMillis)).coerceIn(0f, 1f)

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}
