package dev.eweather.ui.weather.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.AirQuality
import dev.eweather.domain.model.CurrentWeather
import dev.eweather.domain.model.DailyPoint
import dev.eweather.domain.model.HourlyPoint
import dev.eweather.util.AqiDescription
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 2-column grid of 8 weather detail cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailCardsGrid(
    current: CurrentWeather,
    airQuality: AirQuality?,
    hourlyPoints: List<HourlyPoint>,
    todayDaily: DailyPoint?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        val cardMod = Modifier.weight(1f)
        WindCard(current.windSpeed, current.windDirection, cardMod)
        HumidityCard(current.humidity, current.temp, cardMod)
        UvIndexCard(hourlyPoints.firstOrNull()?.uvIndex ?: 0f, cardMod)
        AirQualityCard(airQuality, cardMod)
        VisibilityCard(current.visibility, cardMod)
        PressureCard(current.pressure, hourlyPoints, cardMod)
        PrecipitationCard(todayDaily?.precipSum ?: 0f, hourlyPoints, cardMod)
        FeelsLikeCard(current.feelsLike, current.temp, cardMod)
    }
}

// --- Card header helper ---

@Composable
private fun CardHeader(icon: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
        )
    }
}

// --- Wind ---

@Composable
private fun WindCard(speed: Float, direction: Int, modifier: Modifier) {
    val animatedAngle by animateFloatAsState(
        targetValue = direction.toFloat(),
        animationSpec = tween(1000),
        label = "wind_needle",
    )

    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83D\uDCA8", "Wind")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${speed.roundToInt()} km/h ${cardinalDirection(direction)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        // Compass needle
        Canvas(modifier = Modifier.size(40.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.width / 2 - 4
            drawCircle(Color.White.copy(alpha = 0.2f), r, Offset(cx, cy), style = Stroke(1.5f))
            val angleRad = (animatedAngle - 90) * PI.toFloat() / 180f
            drawLine(
                Color.White,
                Offset(cx, cy),
                Offset(cx + cos(angleRad) * r * 0.8f, cy + sin(angleRad) * r * 0.8f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

// --- Humidity ---

@Composable
private fun HumidityCard(humidity: Int, temp: Float, modifier: Modifier) {
    val dewPoint = temp - (100 - humidity) / 5f

    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83D\uDCA7", "Humidity")
        Spacer(Modifier.height(8.dp))
        Text("$humidity%", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text("Dew point: ${formatTemp(dewPoint)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        // Arc gauge
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val w = size.width
            drawRoundRect(Color.White.copy(alpha = 0.15f), size = Size(w, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
            drawRoundRect(Color(0xFF4FC3F7), size = Size(w * humidity / 100f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
        }
    }
}

// --- UV Index ---

@Composable
private fun UvIndexCard(uvIndex: Float, modifier: Modifier) {
    val (label, color) = when {
        uvIndex <= 2 -> "Low" to Color(0xFF4CAF50)
        uvIndex <= 5 -> "Moderate" to Color(0xFFFFEB3B)
        uvIndex <= 7 -> "High" to Color(0xFFFF9800)
        uvIndex <= 10 -> "Very High" to Color(0xFFF44336)
        else -> "Extreme" to Color(0xFF9C27B0)
    }
    val advice = when {
        uvIndex <= 2 -> "No protection needed"
        uvIndex <= 5 -> "Wear sunscreen"
        uvIndex <= 7 -> "Reduce sun exposure"
        uvIndex <= 10 -> "Extra protection needed"
        else -> "Avoid sun exposure"
    }

    Column(modifier = modifier.glassCard()) {
        CardHeader("☀\uFE0F", "UV Index")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${uvIndex.roundToInt()}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Text(advice, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

// --- Air Quality ---

@Composable
private fun AirQualityCard(airQuality: AirQuality?, modifier: Modifier) {
    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83C\uDF2C\uFE0F", "Air Quality")
        Spacer(Modifier.height(8.dp))
        if (airQuality?.europeanAqi != null) {
            val level = AqiDescription.europeanAqiLevel(airQuality.europeanAqi)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${airQuality.europeanAqi}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.size(10.dp)) { drawCircle(level.color) }
                Spacer(Modifier.width(4.dp))
                Text(level.label, fontSize = 14.sp, color = level.color)
            }
            Text(level.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        } else {
            Text("—", fontSize = 24.sp, color = Color.White.copy(alpha = 0.4f))
            Text("Not available", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

// --- Visibility ---

@Composable
private fun VisibilityCard(visibilityMeters: Float, modifier: Modifier) {
    val km = visibilityMeters / 1000f
    val label = when {
        km > 10 -> "Excellent"
        km > 5 -> "Good"
        km > 1 -> "Moderate"
        else -> "Poor"
    }

    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83D\uDC41\uFE0F", "Visibility")
        Spacer(Modifier.height(8.dp))
        Text("${km.roundToInt()} km", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

// --- Pressure ---

@Composable
private fun PressureCard(pressure: Float, hourlyPoints: List<HourlyPoint>, modifier: Modifier) {
    // Simple trend from hourly data (not available directly, use a placeholder)
    val trend = "Steady" // Would compare hourly pressure if available

    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83D\uDCCA", "Pressure")
        Spacer(Modifier.height(8.dp))
        Text("${pressure.roundToInt()} hPa", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(trend, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

// --- Precipitation ---

@Composable
private fun PrecipitationCard(todayTotal: Float, hourlyPoints: List<HourlyPoint>, modifier: Modifier) {
    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83C\uDF27\uFE0F", "Precipitation")
        Spacer(Modifier.height(8.dp))
        Text("${"%.1f".format(todayTotal)} mm today", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        // Next 12h bar chart
        val next12 = hourlyPoints.take(12)
        if (next12.isNotEmpty()) {
            val maxPrecip = next12.maxOf { it.precipAmount }.coerceAtLeast(1f)
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                val barWidth = size.width / next12.size
                next12.forEachIndexed { i, point ->
                    val barHeight = (point.precipAmount / maxPrecip) * size.height
                    if (barHeight > 0.5f) {
                        drawRect(
                            Color(0xFF4FC3F7).copy(alpha = 0.7f),
                            topLeft = Offset(i * barWidth + 1f, size.height - barHeight),
                            size = Size(barWidth - 2f, barHeight),
                        )
                    }
                }
            }
        }
    }
}

// --- Feels Like ---

@Composable
private fun FeelsLikeCard(feelsLike: Float, actualTemp: Float, modifier: Modifier) {
    val explanation = when {
        feelsLike < actualTemp - 2 -> "Wind makes it feel cooler"
        feelsLike > actualTemp + 2 -> "Humidity makes it feel warmer"
        else -> "Similar to actual temperature"
    }

    Column(modifier = modifier.glassCard()) {
        CardHeader("\uD83C\uDF21\uFE0F", "Feels Like")
        Spacer(Modifier.height(8.dp))
        Text(formatTemp(feelsLike), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(explanation, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

// --- Helpers ---

private fun cardinalDirection(degrees: Int): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[((degrees + 22) / 45) % 8]
}
