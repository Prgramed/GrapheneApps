package com.prgramed.edoist.feature.taskdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prgramed.edoist.domain.model.Priority

@Composable
fun PrioritySelector(
    selected: Priority,
    onSelected: (Priority) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Priority.entries.forEach { priority ->
                IconButton(
                    onClick = { onSelected(priority) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (priority == selected) {
                            Icons.Filled.Flag
                        } else {
                            Icons.Outlined.Flag
                        },
                        contentDescription = priority.displayName,
                        tint = Color(priority.colorArgb),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Text(
            text = selected.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
