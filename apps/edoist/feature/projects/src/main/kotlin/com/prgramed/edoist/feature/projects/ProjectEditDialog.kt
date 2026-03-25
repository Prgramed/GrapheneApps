package com.prgramed.edoist.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.ViewType

private val PresetColors = listOf(
    0xFFD93025L, // Red
    0xFFE67C73L, // Salmon
    0xFFEB8909L, // Orange
    0xFFF6BF26L, // Yellow
    0xFF33B679L, // Sage
    0xFF0B8043L, // Green
    0xFF039BE5L, // Peacock
    0xFF246FE0L, // Blueberry
    0xFF7986CBL, // Lavender
    0xFF8E24AAL, // Grape
    0xFF616161L, // Graphite
    0xFF795548L, // Cocoa
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditDialog(
    project: Project?,
    onSave: (name: String, color: Long, defaultView: ViewType) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(project?.name ?: "") }
    var selectedColor by remember { mutableLongStateOf(project?.color ?: PresetColors.first()) }
    var selectedView by remember { mutableStateOf(project?.defaultView ?: ViewType.LIST) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (project == null) "New Project" else "Edit Project")
        },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (color == selectedColor) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == selectedColor) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Default view",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ViewType.entries.forEach { viewType ->
                        FilterChip(
                            selected = viewType == selectedView,
                            onClick = { selectedView = viewType },
                            label = {
                                Text(
                                    text = when (viewType) {
                                        ViewType.LIST -> "List"
                                        ViewType.BOARD -> "Board"
                                        ViewType.CALENDAR -> "Calendar"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), selectedColor, selectedView) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
