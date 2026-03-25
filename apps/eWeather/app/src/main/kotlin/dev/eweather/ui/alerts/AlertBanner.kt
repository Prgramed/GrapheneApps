package dev.eweather.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eweather.domain.model.AlertSeverity
import dev.eweather.domain.model.WeatherAlert

fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.MINOR -> Color(0xFFFDD835)
    AlertSeverity.MODERATE -> Color(0xFFFB8C00)
    AlertSeverity.SEVERE -> Color(0xFFE53935)
    AlertSeverity.EXTREME -> Color(0xFF8E24AA)
}

@Composable
fun AlertBanner(
    alerts: List<WeatherAlert>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = alerts.isNotEmpty(),
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
    ) {
        val highest = alerts.maxByOrNull { it.severity.ordinal } ?: return@AnimatedVisibility
        val bgColor = severityColor(highest.severity)

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor.copy(alpha = 0.9f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Alert",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (alerts.size == 1) {
                Text(
                    text = "${highest.event} — Tap for details",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "${alerts.size} active alerts — Tap for details",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
