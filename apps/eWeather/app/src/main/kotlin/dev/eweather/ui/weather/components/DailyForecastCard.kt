package dev.eweather.ui.weather.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.DailyPoint
import dev.eweather.util.WmoCode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val CoolBlue = Color(0xFF64B5F6)
private val WarmOrange = Color(0xFFFFB74D)
private val PrecipBlue = Color(0xFF4FC3F7)

/**
 * 10-day forecast card with expandable daily rows and proportional temperature range bars.
 */
@Composable
fun DailyForecastCard(
    dailyPoints: List<DailyPoint>,
    modifier: Modifier = Modifier,
) {
    if (dailyPoints.isEmpty()) return

    val globalMin = dailyPoints.minOf { it.tempMin }
    val globalMax = dailyPoints.maxOf { it.tempMax }
    val globalRange = (globalMax - globalMin).coerceAtLeast(1f)

    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(),
    ) {
        dailyPoints.forEachIndexed { index, day ->
            DailyRow(
                day = day,
                index = index,
                globalMin = globalMin,
                globalRange = globalRange,
                isExpanded = expandedIndex == index,
                onClick = {
                    expandedIndex = if (expandedIndex == index) null else index
                },
            )
            if (index < dailyPoints.lastIndex) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun DailyRow(
    day: DailyPoint,
    index: Int,
    globalMin: Float,
    globalRange: Float,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val dayName = if (index == 0) {
        "Today"
    } else {
        try {
            LocalDate.parse(day.date)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        } catch (_: Exception) { "—" }
    }

    val wmo = WmoCode.describe(day.weatherCode, true)
    val icon = weatherEmoji(wmo.iconCategory, true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Day name
            Text(
                text = dayName,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.width(48.dp),
            )

            // Weather icon
            Text(
                text = icon,
                fontSize = 18.sp,
                modifier = Modifier.width(28.dp),
            )

            // Precipitation
            if (day.precipSum > 0f) {
                Text(
                    text = "\uD83D\uDCA7${day.precipSum.toInt()}%",
                    fontSize = 12.sp,
                    color = PrecipBlue,
                    modifier = Modifier.width(40.dp),
                )
            } else {
                Spacer(Modifier.width(40.dp))
            }

            // Low temp
            Text(
                text = formatTemp(day.tempMin),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.width(32.dp),
            )

            // Temperature range bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            ) {
                TemperatureBar(
                    tempMin = day.tempMin,
                    tempMax = day.tempMax,
                    globalMin = globalMin,
                    globalRange = globalRange,
                )
            }

            // High temp
            Text(
                text = formatTemp(day.tempMax),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.width(32.dp),
            )
        }

        // Expanded detail
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("☀\uFE0F ${day.sunrise.takeLast(5)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("\uD83C\uDF19 ${day.sunset.takeLast(5)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Column {
                    Text("UV ${day.uvIndexMax.toInt()}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("\uD83D\uDCA8 ${day.windSpeedMax.toInt()} km/h", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
                if (day.precipSum > 0f) {
                    Text(
                        "\uD83C\uDF27\uFE0F ${day.precipSum} mm",
                        fontSize = 12.sp,
                        color = PrecipBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureBar(
    tempMin: Float,
    tempMax: Float,
    globalMin: Float,
    globalRange: Float,
) {
    val barStart = ((tempMin - globalMin) / globalRange).coerceIn(0f, 1f)
    val barEnd = ((tempMax - globalMin) / globalRange).coerceIn(0f, 1f)

    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
        val totalWidth = size.width
        val startX = barStart * totalWidth
        val endX = barEnd * totalWidth
        val barWidth = (endX - startX).coerceAtLeast(4f)

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(CoolBlue, WarmOrange),
                startX = startX,
                endX = endX,
            ),
            topLeft = Offset(startX, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}
