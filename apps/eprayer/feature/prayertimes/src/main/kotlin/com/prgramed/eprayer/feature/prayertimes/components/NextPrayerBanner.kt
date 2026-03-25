package com.prgramed.eprayer.feature.prayertimes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prgramed.eprayer.domain.model.PrayerTime
import kotlin.time.Duration

private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)
private val PillBg = Color(0xFF1A2744)

@Composable
fun NextPrayerBanner(
    prayerTime: PrayerTime,
    timeRemaining: Duration?,
    cityName: String? = null,
    hijriDate: String? = null,
    modifier: Modifier = Modifier,
) {
    val displayName = prayerTime.prayer.name.lowercase().replaceFirstChar { it.uppercase() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = displayName,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Column(horizontalAlignment = Alignment.End) {
                if (cityName != null) {
                    Text(
                        text = cityName,
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (hijriDate != null) {
                    Text(
                        text = hijriDate,
                        fontSize = 12.sp,
                        color = Peach.copy(alpha = 0.8f),
                    )
                }
            }
        }

        if (timeRemaining != null) {
            val totalMinutes = timeRemaining.inWholeMinutes
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val nextName = prayerTime.prayer.name.lowercase().replaceFirstChar { it.uppercase() }
            val timeText = when {
                hours > 0 -> "$hours hr $mins mins left until $nextName"
                else -> "$mins mins left until $nextName"
            }
            Text(
                text = timeText,
                fontSize = 14.sp,
                color = Peach,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PillBg)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
