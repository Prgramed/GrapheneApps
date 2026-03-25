package com.prgramed.edoist.feature.projects.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.prgramed.edoist.domain.model.ViewType

@Composable
fun ViewTypeSwitcher(
    currentView: ViewType,
    onViewSelected: (ViewType) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = { onViewSelected(ViewType.LIST) }) {
        Icon(
            imageVector = Icons.Default.ViewList,
            contentDescription = "List view",
            tint = if (currentView == ViewType.LIST) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    IconButton(onClick = { onViewSelected(ViewType.BOARD) }) {
        Icon(
            imageVector = Icons.Default.ViewModule,
            contentDescription = "Board view",
            tint = if (currentView == ViewType.BOARD) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    IconButton(onClick = { onViewSelected(ViewType.CALENDAR) }) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = "Calendar view",
            tint = if (currentView == ViewType.CALENDAR) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
