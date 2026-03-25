package com.prgramed.edoist.feature.inbox.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Task
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun TaskListItem(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    showProject: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PriorityCheckbox(
            isCompleted = task.isCompleted,
            priority = task.priority,
            onCheckedChange = onCheckedChange,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val hasChips = task.dueDate != null || task.labels.isNotEmpty() ||
                (showProject && task.projectId.isNotBlank())

            if (hasChips) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    task.dueDate?.let { date ->
                        DueDateChip(date = date, isOverdue = task.isOverdue)
                    }

                    task.labels.forEach { label ->
                        LabelChip(
                            name = label.name,
                            color = Color(label.color),
                        )
                    }

                    if (showProject && task.projectId.isNotBlank()) {
                        Text(
                            text = task.projectId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (task.priority != Priority.P4 && !task.isCompleted) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = task.priority.displayName,
                tint = Color(task.priority.colorArgb),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun PriorityCheckbox(
    isCompleted: Boolean,
    priority: Priority,
    onCheckedChange: (Boolean) -> Unit,
) {
    val borderColor = Color(priority.colorArgb)

    IconButton(
        onClick = { onCheckedChange(!isCompleted) },
        modifier = Modifier.size(24.dp),
    ) {
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = borderColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, borderColor, CircleShape),
            )
        }
    }
}

@Composable
private fun DueDateChip(
    date: LocalDate,
    isOverdue: Boolean,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val tomorrow = LocalDate(today.year, today.monthNumber, today.dayOfMonth)
        .let { d ->
            val epochDays = d.toEpochDays() + 1
            LocalDate.fromEpochDays(epochDays)
        }

    val displayText = when (date) {
        today -> "Today"
        tomorrow -> "Tomorrow"
        else -> {
            val month = date.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            "$month ${date.dayOfMonth}"
        }
    }

    val textColor = if (isOverdue) {
        Color(0xFFD93025)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
    )
}

@Composable
private fun LabelChip(
    name: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .border(4.dp, color, CircleShape),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
