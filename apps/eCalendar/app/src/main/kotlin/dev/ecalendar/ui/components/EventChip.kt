package dev.ecalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.util.ColorPalette

@Composable
fun EventChip(
    event: CalendarEvent,
    isCompact: Boolean = false,
    onClick: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val color = ColorPalette.forTheme(event.colorHex ?: "#4285F4", isDark)

    if (isCompact) {
        // Small colored dot for month grid
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
        )
    } else {
        // Full colored pill with title for week/day/agenda
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.85f))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = event.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
