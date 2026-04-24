package com.prgramed.edoist.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.edoist.domain.model.TaskGroup
import com.prgramed.edoist.feature.inbox.components.SwipeableTaskItem
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onTaskClick: (String) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Today") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.taskGroups.isEmpty() || uiState.todayCount == 0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "All done for today!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    uiState.taskGroups.forEach { group ->
                        item(key = groupHeaderKey(group)) {
                            TaskGroupHeader(group = group)
                        }

                        items(
                            items = group.tasks,
                            key = { it.id },
                        ) { task ->
                            SwipeableTaskItem(
                                task = task,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.completeTask(task.id)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Task completed",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.uncompleteTask(task.id)
                                            }
                                        }
                                    } else {
                                        viewModel.uncompleteTask(task.id)
                                    }
                                },
                                onClick = { onTaskClick(task.id) },
                                onComplete = {
                                    viewModel.completeTask(task.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Task completed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.uncompleteTask(task.id)
                                        }
                                    }
                                },
                                onSchedule = { onTaskClick(task.id) },
                                showProject = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskGroupHeader(
    group: TaskGroup,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (group) {
            is TaskGroup.Overdue -> {
                Text(
                    text = "Overdue",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFD93025),
                )
                Text(
                    text = "${group.tasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD93025),
                )
            }

            is TaskGroup.ByDate -> {
                Text(
                    text = formatDateHeader(group.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> {
                Text(
                    text = "",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "Today \u2014 $month ${date.dayOfMonth}"
}

private fun groupHeaderKey(group: TaskGroup): String = when (group) {
    is TaskGroup.Overdue -> "header_overdue"
    is TaskGroup.ByDate -> "header_date_${group.date}"
    is TaskGroup.ByPriority -> "header_priority_${group.priority}"
    is TaskGroup.ByProject -> "header_project_${group.project.id}"
    is TaskGroup.ByLabel -> "header_label_${group.label.id}"
    is TaskGroup.BySection -> "header_section_${group.section?.id}"
}
