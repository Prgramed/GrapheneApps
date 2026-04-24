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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.model.TaskGroup
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.usecase.CompleteTaskUseCase
import com.prgramed.edoist.domain.usecase.GetUpcomingTasksUseCase
import com.prgramed.edoist.feature.inbox.components.SwipeableTaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class UpcomingUiState(
    val groups: List<TaskGroup.ByDate> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    getUpcomingTasksUseCase: GetUpcomingTasksUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState: StateFlow<UpcomingUiState> = getUpcomingTasksUseCase(days = 14)
        .map { groups ->
            UpcomingUiState(
                groups = groups,
                totalCount = groups.sumOf { it.tasks.size },
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingUiState())

    fun completeTask(taskId: String) {
        viewModelScope.launch { completeTaskUseCase(taskId) }
    }

    fun uncompleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.uncompleteTask(taskId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    onTaskClick: (String) -> Unit,
    viewModel: UpcomingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Upcoming") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.groups.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Nothing coming up",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    uiState.groups.forEach { group ->
                        item(key = "header_${group.date}") {
                            UpcomingDateHeader(date = group.date)
                        }
                        items(items = group.tasks, key = { it.id }) { task ->
                            SwipeableTaskItem(
                                task = task,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.completeTask(task.id)
                                        scope.launch {
                                            val r = snackbarHostState.showSnackbar(
                                                "Task completed", "Undo", duration = SnackbarDuration.Short,
                                            )
                                            if (r == SnackbarResult.ActionPerformed) viewModel.uncompleteTask(task.id)
                                        }
                                    } else viewModel.uncompleteTask(task.id)
                                },
                                onClick = { onTaskClick(task.id) },
                                onComplete = {
                                    viewModel.completeTask(task.id)
                                    scope.launch {
                                        val r = snackbarHostState.showSnackbar(
                                            "Task completed", "Undo", duration = SnackbarDuration.Short,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) viewModel.uncompleteTask(task.id)
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
private fun UpcomingDateHeader(date: LocalDate) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val tomorrow = remember(today) { LocalDate.fromEpochDays(today.toEpochDays() + 1) }
    val dayName = when (date) {
        today -> "Today"
        tomorrow -> "Tomorrow"
        else -> {
            val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$dow, $month ${date.dayOfMonth}"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.titleSmall,
            color = if (date == today) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
