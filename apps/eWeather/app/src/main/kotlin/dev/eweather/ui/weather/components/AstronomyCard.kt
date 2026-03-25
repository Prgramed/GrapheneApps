package dev.eweather.ui.weather.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.DailyPoint
import dev.eweather.domain.model.MoonPhaseInfo
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ArcGold = Color(0xFFFFB347)
private val ArcOrange = Color(0xFFFF6B35)
private val SunYellow = Color(0xFFFFD54F)

/**
 * Astronomy card with sunrise/sunset arc, sun position, moon phase, and daylight duration.
 */
@Composable
fun AstronomyCard(
    todayDaily: DailyPoint,
    moonPhase: MoonPhaseInfo,
    modifier: Modifier = Modifier,
) {
    val sunriseMillis = parseIsoToMillis(todayDaily.sunrise)
    val sunsetMillis = parseIsoToMillis(todayDaily.sunset)
    val nowMillis = System.currentTimeMillis()

    val sunProgress = if (sunriseMillis > 0 && sunsetMillis > sunriseMillis) {
        ((nowMillis - sunriseMillis).toFloat() / (sunsetMillis - sunriseMillis)).coerceIn(0f, 1f)
    } else 0.5f

    val daylightMinutes = if (sunsetMillis > sunriseMillis) {
        ((sunsetMillis - sunriseMillis) / 60000).toInt()
    } else 0
    val daylightHours = daylightMinutes / 60
    val daylightMins = daylightMinutes % 60

    // Golden hour: within 2 hours before sunset
    val goldenHourMinutes = if (sunsetMillis > nowMillis) {
        val minsUntilSunset = ((sunsetMillis - nowMillis) / 60000).toInt()
        if (minsUntilSunset in 0..120) minsUntilSunset else null
    } else null

    val sunriseTime = formatTime(todayDaily.sunrise)
    val sunsetTime = formatTime(todayDaily.sunset)
    val a11y = "Sunrise at $sunriseTime, sunset at $sunsetTime. " +
        "Daylight ${daylightHours}h ${daylightMins}m. " +
        "Moon: ${moonPhase.phaseName}, ${(moonPhase.illumination * 100).toInt()}% illuminated"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .semantics { contentDescription = a11y },
    ) {
        // Sun arc
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
        ) {
            val w = size.width
            val h = size.height
            val arcRadius = w * 0.4f
            val cx = w / 2f
            val cy = h - 10f

            // Draw arc (semicircle, 180° from left to right)
            drawArc(
                color = ArcGold.copy(alpha = 0.3f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )

            // Dashed horizon line
            drawLine(
                Color.White.copy(alpha = 0.2f),
                Offset(cx - arcRadius - 20f, cy),
                Offset(cx + arcRadius + 20f, cy),
                strokeWidth = 1f,
            )

            // Sun position on arc
            val sunAngle = PI.toFloat() * (1f - sunProgress) // 180° to 0° (left to right)
            val sunX = cx + cos(sunAngle) * arcRadius
            val sunY = cy - sin(sunAngle) * arcRadius

            // Glow
            drawCircle(
                SunYellow.copy(alpha = 0.2f),
                radius = 16f,
                center = Offset(sunX, sunY),
            )
            // Sun dot
            drawCircle(
                SunYellow,
                radius = 6f,
                center = Offset(sunX, sunY),
            )
        }

        // Sunrise / Sunset labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "☀\uFE0F ${formatTime(todayDaily.sunrise)}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "\uD83C\uDF19 ${formatTime(todayDaily.sunset)}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Daylight duration + golden hour
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Daylight: ${daylightHours}h ${daylightMins}m",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            if (goldenHourMinutes != null) {
                Text(
                    text = "Golden hour in ${goldenHourMinutes / 60}h ${goldenHourMinutes % 60}m",
                    fontSize = 12.sp,
                    color = ArcGold,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Moon section
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(moonPhase.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = moonPhase.phaseName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = "${(moonPhase.illumination * 100).toInt()}% illuminated",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun formatTime(isoDateTime: String): String {
    return try {
        isoDateTime.takeLast(5) // "05:40" from "2026-03-23T05:40"
    } catch (_: Exception) { "—" }
}

private fun parseIsoToMillis(iso: String): Long {
    return try {
        LocalDateTime.parse(iso)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) { 0L }
}
