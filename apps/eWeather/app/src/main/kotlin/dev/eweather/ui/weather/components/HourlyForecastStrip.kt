package dev.eweather.ui.weather.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.HourlyPoint
import dev.eweather.util.IconCategory
import dev.eweather.util.WmoCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PrecipColor = Color(0xFF4FC3F7)
private val HourFormatter12 = DateTimeFormatter.ofPattern("h a")
private val CurrentHighlight = RoundedCornerShape(12.dp)

/**
 * Horizontal scrolling strip showing 24 hours of forecast.
 * Glass card wrapper with hourly items: time, icon, temp, precip bar.
 */
@Composable
fun HourlyForecastStrip(
    hourlyPoints: List<HourlyPoint>,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    // Filter to show from current hour onwards (next 24h), not from midnight
    val startIndex = hourlyPoints.indexOfFirst { it.timestamp >= now }.coerceAtLeast(0)
    val points = hourlyPoints.drop(startIndex).take(24)
    val firstTimestamp = points.firstOrNull()?.timestamp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(points) { point ->
                val isCurrent = point.timestamp == firstTimestamp
                HourlyItem(
                    point = point,
                    isCurrent = isCurrent,
                )
            }
        }
    }
}

@Composable
private fun HourlyItem(
    point: HourlyPoint,
    isCurrent: Boolean,
) {
    val timeLabel = if (isCurrent) {
        "Now"
    } else {
        try {
            Instant.ofEpochMilli(point.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(HourFormatter12)
                .replace(" ", "\n")
        } catch (_: Exception) { "—" }
    }

    val wmo = WmoCode.describe(point.weatherCode, point.isDay)
    val icon = weatherEmoji(wmo.iconCategory, point.isDay)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .then(
                if (isCurrent) Modifier.background(
                    Color.White.copy(alpha = 0.1f),
                    CurrentHighlight,
                ) else Modifier,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        // Time
        Text(
            text = timeLabel,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )

        Spacer(Modifier.height(6.dp))

        // Weather icon
        Text(
            text = icon,
            fontSize = 20.sp,
        )

        Spacer(Modifier.height(4.dp))

        // Temperature
        Text(
            text = formatTemp(point.temp),
            fontSize = if (isCurrent) 16.sp else 14.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
        )

        Spacer(Modifier.height(4.dp))

        // Precipitation probability bar
        if (point.precipProbability > 10) {
            val barHeight = (point.precipProbability / 100f * 20f).coerceIn(2f, 20f)
            val barAlpha = (point.precipProbability / 100f).coerceIn(0.3f, 1f)
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(barHeight.dp)
                    .background(
                        PrecipColor.copy(alpha = barAlpha),
                        RoundedCornerShape(2.dp),
                    ),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

internal fun weatherEmoji(category: IconCategory, isDay: Boolean): String = when (category) {
    IconCategory.CLEAR -> if (isDay) "☀\uFE0F" else "\uD83C\uDF19"
    IconCategory.PARTLY_CLOUDY -> if (isDay) "⛅" else "☁\uFE0F"
    IconCategory.OVERCAST -> "☁\uFE0F"
    IconCategory.FOG -> "\uD83C\uDF2B\uFE0F"
    IconCategory.DRIZZLE -> "\uD83C\uDF27\uFE0F"
    IconCategory.RAIN -> "\uD83C\uDF27\uFE0F"
    IconCategory.SNOW -> "\uD83C\uDF28\uFE0F"
    IconCategory.STORM -> "⛈\uFE0F"
    IconCategory.HAIL -> "⛈\uFE0F"
}
