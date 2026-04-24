package dev.ecalendar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ecalendar.data.preferences.CalendarView
import dev.ecalendar.sync.SyncState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarHeader(
    activeDate: LocalDate,
    activeView: CalendarView,
    syncState: SyncState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onViewSelected: (CalendarView) -> Unit,
    onAccounts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Suppress Material3's automatic 48dp minimum interactive component size for
    // ALL interactive children (IconButtons, TextButton, clickable Text). Without
    // this, each element's touch target extends ~20dp below its visual bounds,
    // cascading through both header rows and overlapping the month grid cells —
    // making the top portion of date cells non-clickable.
    CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp,
    ) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Month + Year
        Text(
            text = activeDate.format(MONTH_YEAR_FORMAT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        // Sync status dot
        val syncColor = when (syncState) {
            is SyncState.Syncing -> Color(0xFFFFA726) // Amber
            is SyncState.Error -> Color(0xFFEF5350) // Red
            is SyncState.LastSyncedAt -> Color(0xFF66BB6A) // Green
            is SyncState.Idle -> Color.Transparent
        }
        if (syncColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(syncColor),
            )
            Spacer(Modifier.width(8.dp))
        }

        // Settings button
        if (onAccounts != null) {
            IconButton(onClick = onAccounts, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
            }
        }

        // Navigation arrows
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
        }
        val pulseScale = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()
        val isToday = activeDate == java.time.LocalDate.now()
        TextButton(
            onClick = {
                if (isToday) {
                    scope.launch {
                        pulseScale.animateTo(1.15f, spring(dampingRatio = 0.3f, stiffness = 400f))
                        pulseScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
                    }
                }
                onToday()
            },
            modifier = Modifier.scale(pulseScale.value),
        ) {
            Text("Today", style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
        }
    }

    // View switcher
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarView.entries.forEach { view ->
            val isActive = view == activeView
            val label = when (view) {
                CalendarView.MONTH -> "M"
                CalendarView.WEEK -> "W"
                CalendarView.DAY -> "D"
                CalendarView.AGENDA -> "A"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    )
                    // Use interactionSource + no indication to suppress the 48dp
                    // minimum interactive component size. Without this, the touch
                    // target extends ~20dp below the visual bounds, overlapping
                    // with the top rows of the month grid and eating their taps.
                    .clickable { onViewSelected(view) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    } // CompositionLocalProvider
}
