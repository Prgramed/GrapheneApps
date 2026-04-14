package com.prgramed.edoist.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.model.ViewType
import com.prgramed.edoist.feature.inbox.components.TaskListItem
import com.prgramed.edoist.feature.projects.components.BoardColumn
import com.prgramed.edoist.feature.projects.components.SectionHeader
import com.prgramed.edoist.feature.projects.components.ViewTypeSwitcher
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onTaskClick: (String) -> Unit,
    onAddTask: (projectId: String, sectionId: String?) -> Unit = { _, _ -> },
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.project?.let { project ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(project.color)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = uiState.project?.name ?: "")
                    }
                },
                actions = {
                    ViewTypeSwitcher(
                        currentView = uiState.viewType,
                        onViewSelected = viewModel::switchView,
                    )
                },
            )
        },
        floatingActionButton = {
            val projectId = uiState.project?.id
            if (projectId != null) {
                FloatingActionButton(onClick = { onAddTask(projectId, null) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        },
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

            else -> {
                when (uiState.viewType) {
                    ViewType.LIST -> ListViewContent(
                        unsectionedTasks = uiState.unsectionedTasks,
                        sections = uiState.sections,
                        onTaskClick = onTaskClick,
                        onCheckedChange = { taskId, checked ->
                            if (checked) viewModel.completeTask(taskId)
                        },
                        onToggleSection = viewModel::toggleSectionCollapsed,
                        onCreateSection = { name -> viewModel.createSection(name) },
                        onDeleteSection = { sectionId -> viewModel.deleteSection(sectionId) },
                        onAddTask = { sectionId ->
                            uiState.project?.id?.let { pid -> onAddTask(pid, sectionId) }
                        },
                        projectColor = uiState.project?.color,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    ViewType.BOARD -> BoardViewContent(
                        unsectionedTasks = uiState.unsectionedTasks,
                        sections = uiState.sections,
                        onTaskClick = onTaskClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    ViewType.CALENDAR -> CalendarViewContent(
                        unsectionedTasks = uiState.unsectionedTasks,
                        sections = uiState.sections,
                        onTaskClick = onTaskClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListViewContent(
    unsectionedTasks: List<Task>,
    sections: List<Section>,
    onTaskClick: (String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onToggleSection: (String) -> Unit,
    onCreateSection: (String) -> Unit = {},
    onDeleteSection: (String) -> Unit = {},
    onAddTask: (sectionId: String) -> Unit = {},
    projectColor: Long? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        // Unsectioned tasks at top
        items(
            items = unsectionedTasks,
            key = { "unsectioned_${it.id}" },
        ) { task ->
            TaskListItem(
                task = task,
                onCheckedChange = { checked -> onCheckedChange(task.id, checked) },
                onClick = { onTaskClick(task.id) },
                projectColor = projectColor,
            )
        }

        // Sections with tasks
        sections.forEach { section ->
            item(key = "section_header_${section.id}") {
                SectionHeader(
                    section = section,
                    taskCount = section.tasks.size,
                    isCollapsed = section.isCollapsed,
                    onToggle = { onToggleSection(section.id) },
                    onAddTask = { onAddTask(section.id) },
                    onDelete = { onDeleteSection(section.id) },
                )
            }

            if (!section.isCollapsed) {
                items(
                    items = section.tasks,
                    key = { "section_${section.id}_task_${it.id}" },
                ) { task ->
                    TaskListItem(
                        task = task,
                        onCheckedChange = { checked -> onCheckedChange(task.id, checked) },
                        onClick = { onTaskClick(task.id) },
                    )
                }
            }
        }

        // Add section button
        item {
            var showSectionDialog by remember { mutableStateOf(false) }
            if (showSectionDialog) {
                var sectionName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showSectionDialog = false },
                    title = { Text("New section") },
                    text = {
                        OutlinedTextField(
                            value = sectionName,
                            onValueChange = { sectionName = it },
                            label = { Text("Section name") },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (sectionName.isNotBlank()) {
                                    onCreateSection(sectionName)
                                    showSectionDialog = false
                                }
                            },
                        ) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSectionDialog = false }) { Text("Cancel") }
                    },
                )
            }
            TextButton(
                onClick = { showSectionDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add section")
            }
        }
    }
}

@Composable
private fun BoardViewContent(
    unsectionedTasks: List<Task>,
    sections: List<Section>,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = buildList {
        add("Unsectioned" to unsectionedTasks)
        sections.forEach { section ->
            add(section.name to section.tasks)
        }
    }

    val pagerState = rememberPagerState(pageCount = { columns.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
    ) { page ->
        val (sectionName, tasks) = columns[page]
        BoardColumn(
            sectionName = sectionName,
            tasks = tasks,
            onTaskClick = onTaskClick,
        )
    }
}

@Composable
private fun CalendarViewContent(
    unsectionedTasks: List<Task>,
    sections: List<Section>,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val allTasks = remember(unsectionedTasks, sections) {
        unsectionedTasks + sections.flatMap { it.tasks }
    }

    // Build date-to-task-count map for the current month
    val firstDayOfMonth = remember(today) { LocalDate(today.year, today.month, 1) }
    val lastDay = remember(today) {
        when (today.monthNumber) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (today.year % 4 == 0 && (today.year % 100 != 0 || today.year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    val tasksByDate = remember(allTasks) {
        allTasks.filter { it.dueDate != null }.groupBy { it.dueDate!! }
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Month header
        Text(
            text = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Day of week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Calendar grid
        val firstDayOffset = (firstDayOfMonth.dayOfWeek.ordinal) // Monday = 0
        var currentDay = 1

        // Calculate number of weeks needed
        val totalCells = firstDayOffset + lastDay
        val weeks = (totalCells + 6) / 7

        repeat(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { dayOfWeek ->
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - firstDayOffset + 1

                    if (dayNumber in 1..lastDay) {
                        val date = LocalDate(today.year, today.monthNumber, dayNumber)
                        val hasTask = tasksByDate.containsKey(date)
                        val isToday = date == today

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$dayNumber",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )

                            if (hasTask) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Task list below calendar for today
        val todayTasks = tasksByDate[today] ?: emptyList()
        if (todayTasks.isNotEmpty()) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            todayTasks.forEach { task ->
                TaskListItem(
                    task = task,
                    onCheckedChange = { },
                    onClick = { onTaskClick(task.id) },
                )
            }
        }
    }
}
