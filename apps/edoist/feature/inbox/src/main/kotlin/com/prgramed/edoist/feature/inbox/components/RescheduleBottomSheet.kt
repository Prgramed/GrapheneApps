package com.prgramed.edoist.feature.inbox.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NextWeek
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleBottomSheet(
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val tomorrow = remember(today) { today.plus(1, DateTimeUnit.DAY) }
    val nextMonday = remember(today) {
        var d = today.plus(1, DateTimeUnit.DAY)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plus(1, DateTimeUnit.DAY)
        d
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Reschedule",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            RescheduleOption(
                icon = Icons.Default.CalendarToday,
                label = "Today",
                sublabel = today.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                onClick = { onDateSelected(today); onDismiss() },
            )
            RescheduleOption(
                icon = Icons.Default.WbSunny,
                label = "Tomorrow",
                sublabel = tomorrow.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                onClick = { onDateSelected(tomorrow); onDismiss() },
            )
            RescheduleOption(
                icon = Icons.Default.NextWeek,
                label = "Next Monday",
                sublabel = "${nextMonday.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${nextMonday.dayOfMonth}",
                onClick = { onDateSelected(nextMonday); onDismiss() },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RescheduleOption(
    icon: ImageVector,
    label: String,
    sublabel: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
