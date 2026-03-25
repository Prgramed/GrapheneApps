package dev.eweather.ui.weather.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.CurrentWeather
import dev.eweather.domain.model.DailyPoint
import dev.eweather.util.WmoCode
import kotlin.math.roundToInt

private val textShadow = Shadow(
    color = Color.Black.copy(alpha = 0.3f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)

/**
 * Hero element at the top of the weather screen.
 * Floats directly over the sky background — no glass card.
 */
@Composable
fun CurrentConditionsCard(
    current: CurrentWeather,
    todayDaily: DailyPoint?,
    locationName: String,
    onLocationClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val description = WmoCode.describe(current.weatherCode, current.isDay).label

    val a11yDescription = "${formatTemp(current.temp)}, $description, $locationName"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 16.dp)
            .semantics { contentDescription = a11yDescription },
    ) {
        // City name — clickable
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onLocationClick),
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = locationName,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    shadow = textShadow,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Large temperature
        Text(
            text = formatTemp(current.temp),
            style = TextStyle(
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White,
                shadow = textShadow,
            ),
        )

        // Weather description
        Text(
            text = description,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.7f),
                shadow = textShadow,
            ),
        )

        Spacer(Modifier.height(4.dp))

        // Feels like
        Text(
            text = "Feels like ${formatTemp(current.feelsLike)}",
            style = TextStyle(
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                shadow = textShadow,
            ),
        )

        // High / Low
        if (todayDaily != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "H:${formatTemp(todayDaily.tempMax)}  L:${formatTemp(todayDaily.tempMin)}",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    shadow = textShadow,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onRadarClick) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "Radar map",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

fun formatTemp(temp: Float): String = "${temp.roundToInt()}°"
