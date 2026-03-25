package com.prgramed.edoist.feature.taskdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.feature.taskdetail.components.DueDateSelector
import com.prgramed.edoist.feature.taskdetail.components.LabelSelector
import com.prgramed.edoist.feature.taskdetail.components.PrioritySelector
import com.prgramed.edoist.feature.taskdetail.components.ProjectSelector
import com.prgramed.edoist.feature.taskdetail.components.RecurrenceSelector
import com.prgramed.edoist.feature.taskdetail.components.SubtaskList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var showLabelSheet by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }

    // Navigate back after save or deletion
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onBack()
    }

    // Auto-focus title field for new tasks
    LaunchedEffect(uiState.isNewTask) {
        if (uiState.isNewTask) {
            focusRequester.requestFocus()
        }
    }

    if (showLabelSheet) {
        LabelSelector(
            availableLabels = uiState.availableLabels,
            selectedIds = uiState.selectedLabelIds,
            onToggle = viewModel::onLabelToggled,
            onDismiss = { showLabelSheet = false },
        )
    }

    if (showRecurrenceDialog) {
        RecurrenceSelector(
            current = uiState.recurrenceRule,
            onSelected = { rule ->
                viewModel.onRecurrenceSelected(rule)
                showRecurrenceDialog = false
            },
            onDismiss = { showRecurrenceDialog = false },
        )
    }

    // Find sections for the selected project
    val selectedProject = uiState.availableProjects.find { it.id == uiState.projectId }
    val sections = selectedProject?.sections ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.title.isNotBlank() && !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(text = "Save")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Title
            item {
                TextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChanged,
                    placeholder = {
                        Text(
                            text = "Task name",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                )
            }

            // Description
            item {
                TextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    placeholder = {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    minLines = 2,
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Project selector
            item {
                ProjectSelector(
                    projects = uiState.availableProjects,
                    selectedId = uiState.projectId,
                    onSelected = viewModel::onProjectSelected,
                )
            }

            // Section selector (if project has sections)
            if (sections.isNotEmpty()) {
                item {
                    SectionSelectorRow(
                        sections = sections,
                        selectedId = uiState.sectionId,
                        onSelected = viewModel::onSectionSelected,
                    )
                }
            }

            // Priority selector
            item {
                PrioritySelector(
                    selected = uiState.priority,
                    onSelected = viewModel::onPrioritySelected,
                )
            }

            // Due date selector
            item {
                DueDateSelector(
                    dueDate = uiState.dueDate,
                    dueTime = uiState.dueTime,
                    onDateSelected = viewModel::onDueDateSelected,
                    onTimeSelected = viewModel::onDueTimeSelected,
                    onNaturalDateParsed = { /* Handled via title parsing */ },
                )
            }

            // Recurrence selector row
            item {
                RecurrenceRow(
                    recurrenceRule = uiState.recurrenceRule,
                    onClick = { showRecurrenceDialog = true },
                )
            }

            // Labels row
            item {
                LabelsRow(
                    selectedLabels = uiState.availableLabels.filter { it.id in uiState.selectedLabelIds },
                    onClick = { showLabelSheet = true },
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Subtasks
            item {
                SubtaskList(
                    subtasks = uiState.subtasks,
                    newSubtaskTitle = uiState.newSubtaskTitle,
                    onNewSubtaskTitleChanged = viewModel::onNewSubtaskTitleChanged,
                    onAddSubtask = viewModel::addSubtask,
                    onToggleSubtask = viewModel::toggleSubtask,
                    onRemoveSubtask = viewModel::removeSubtask,
                )
            }

            // Delete button (only for existing tasks)
            if (!uiState.isNewTask) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = viewModel::delete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Delete task",
                            color = Color(0xFFD93025),
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionSelectorRow(
    sections: List<Section>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSection = sections.find { it.id == selectedId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ViewList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = { expanded = true }) {
            Text(
                text = selectedSection?.name ?: "No section",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("No section") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            sections.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.name) },
                    onClick = {
                        onSelected(section.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RecurrenceRow(
    recurrenceRule: RecurrenceRule?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onClick) {
            Text(
                text = recurrenceRule?.let { rule ->
                    buildString {
                        if (rule.interval > 1) append("Every ${rule.interval} ")
                        append(
                            when (rule.frequency) {
                                RecurrenceRule.Frequency.DAILY -> if (rule.interval > 1) "days" else "Daily"
                                RecurrenceRule.Frequency.WEEKLY -> if (rule.interval > 1) "weeks" else "Weekly"
                                RecurrenceRule.Frequency.MONTHLY -> if (rule.interval > 1) "months" else "Monthly"
                                RecurrenceRule.Frequency.YEARLY -> if (rule.interval > 1) "years" else "Yearly"
                            },
                        )
                    }
                } ?: "No repeat",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LabelsRow(
    selectedLabels: List<com.prgramed.edoist.domain.model.Label>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedLabels.isEmpty()) {
            TextButton(onClick = onClick) {
                Text(
                    text = "Add labels",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedLabels.forEach { label ->
                    FilterChip(
                        selected = true,
                        onClick = onClick,
                        label = { Text(label.name) },
                    )
                }
            }
        }
    }
}
