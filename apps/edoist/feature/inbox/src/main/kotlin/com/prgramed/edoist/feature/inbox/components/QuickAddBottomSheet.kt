package com.prgramed.edoist.feature.inbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Project
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onTaskCreated: (title: String, projectId: String?, priority: Priority?, dueDate: LocalDate?) -> Unit,
    projects: List<Project>,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf<Priority?>(null) }
    var selectedDueDate by remember { mutableStateOf<LocalDate?>(null) }

    var showProjectDropdown by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            text = "Task name",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                )

                IconButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onTaskCreated(title.trim(), selectedProjectId, selectedPriority, selectedDueDate)
                        }
                    },
                    enabled = title.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Add task",
                        tint = if (title.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Project selector
                FilterChip(
                    selected = selectedProjectId != null,
                    onClick = { showProjectDropdown = true },
                    label = {
                        Text(
                            text = selectedProjectId?.let { id ->
                                projects.find { it.id == id }?.name
                            } ?: "Project",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )

                DropdownMenu(
                    expanded = showProjectDropdown,
                    onDismissRequest = { showProjectDropdown = false },
                ) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProjectId = project.id
                                showProjectDropdown = false
                            },
                        )
                    }
                }

                // Priority selector
                FilterChip(
                    selected = selectedPriority != null && selectedPriority != Priority.P4,
                    onClick = {
                        selectedPriority = when (selectedPriority) {
                            null, Priority.P4 -> Priority.P1
                            Priority.P1 -> Priority.P2
                            Priority.P2 -> Priority.P3
                            Priority.P3 -> Priority.P4
                        }
                    },
                    label = {
                        Text(
                            text = selectedPriority?.displayName ?: "Priority",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = selectedPriority?.let { Color(it.colorArgb) }
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // Date chip
                FilterChip(
                    selected = selectedDueDate != null,
                    onClick = {
                        // Toggle today as a simple default; a full date picker integration
                        // would be wired in by the host screen.
                        selectedDueDate = if (selectedDueDate == null) {
                            Clock.System.todayIn(
                                TimeZone.currentSystemDefault(),
                            )
                        } else {
                            null
                        }
                    },
                    label = {
                        Text(
                            text = selectedDueDate?.let { date ->
                                val today = Clock.System.todayIn(
                                    TimeZone.currentSystemDefault(),
                                )
                                when (date) {
                                    today -> "Today"
                                    else -> {
                                        val month = date.month.name.take(3).lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                        "$month ${date.dayOfMonth}"
                                    }
                                }
                            } ?: "Date",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}
