package com.prgramed.edoist.feature.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.LabelRepository
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.usecase.CreateTaskUseCase
import com.prgramed.edoist.domain.usecase.ParseNaturalDateUseCase
import com.prgramed.edoist.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val labelRepository: LabelRepository,
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val parseNaturalDateUseCase: ParseNaturalDateUseCase,
) : ViewModel() {

    private val taskId: String? = savedStateHandle["taskId"]
    private val _uiState = MutableStateFlow(TaskDetailUiState(isNewTask = taskId == null))
    val uiState = _uiState.asStateFlow()

    private var originalTask: Task? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load available projects and labels
            val projects = projectRepository.observeAllActive().first()
            val labels = labelRepository.observeAll().first()

            _uiState.update { state ->
                state.copy(
                    availableProjects = projects,
                    availableLabels = labels,
                    projectId = state.projectId.ifEmpty {
                        projects.find { it.isInbox }?.id ?: projects.firstOrNull()?.id ?: ""
                    },
                )
            }

            // Load existing task if editing
            if (taskId != null) {
                val task = taskRepository.getTaskDetail(taskId).first()
                if (task != null) {
                    originalTask = task
                    val subtasks = taskRepository.getSubtasks(taskId).first()
                    _uiState.update { state ->
                        state.copy(
                            title = task.title,
                            description = task.description,
                            projectId = task.projectId,
                            sectionId = task.sectionId,
                            priority = task.priority,
                            dueDate = task.dueDate,
                            dueTime = task.dueTime,
                            recurrenceRule = task.recurrenceRule,
                            labels = task.labels,
                            selectedLabelIds = task.labels.map { it.id }.toSet(),
                            subtasks = subtasks,
                            isNewTask = false,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(title = title) }

        // Try to parse natural date from title
        val parsed = parseNaturalDateUseCase(title)
        if (parsed.date != null) {
            _uiState.update { state ->
                state.copy(
                    dueDate = parsed.date,
                    dueTime = parsed.time ?: state.dueTime,
                    recurrenceRule = parsed.recurrenceRule ?: state.recurrenceRule,
                )
            }
        }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onProjectSelected(projectId: String) {
        _uiState.update { it.copy(projectId = projectId, sectionId = null) }
    }

    fun onSectionSelected(sectionId: String?) {
        _uiState.update { it.copy(sectionId = sectionId) }
    }

    fun onPrioritySelected(priority: Priority) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun onDueDateSelected(date: LocalDate?) {
        _uiState.update { it.copy(dueDate = date) }
    }

    fun onDueTimeSelected(time: LocalTime?) {
        _uiState.update { it.copy(dueTime = time) }
    }

    fun onRecurrenceSelected(recurrenceRule: RecurrenceRule?) {
        _uiState.update { it.copy(recurrenceRule = recurrenceRule) }
    }

    fun onLabelToggled(labelId: String) {
        _uiState.update { state ->
            val newIds = if (labelId in state.selectedLabelIds) {
                state.selectedLabelIds - labelId
            } else {
                state.selectedLabelIds + labelId
            }
            state.copy(selectedLabelIds = newIds)
        }
    }

    fun onNewSubtaskTitleChanged(title: String) {
        _uiState.update { it.copy(newSubtaskTitle = title) }
    }

    fun addSubtask() {
        val title = _uiState.value.newSubtaskTitle.trim()
        if (title.isBlank()) return

        val subtask = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            projectId = _uiState.value.projectId,
            parentTaskId = taskId,
        )

        _uiState.update { state ->
            state.copy(
                subtasks = state.subtasks + subtask,
                newSubtaskTitle = "",
            )
        }
    }

    fun removeSubtask(subtaskId: String) {
        _uiState.update { state ->
            state.copy(subtasks = state.subtasks.filter { it.id != subtaskId })
        }
    }

    fun toggleSubtask(subtaskId: String) {
        _uiState.update { state ->
            state.copy(
                subtasks = state.subtasks.map { subtask ->
                    if (subtask.id == subtaskId) {
                        subtask.copy(isCompleted = !subtask.isCompleted)
                    } else {
                        subtask
                    }
                },
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val selectedLabels = state.availableLabels.filter { it.id in state.selectedLabelIds }

            if (state.isNewTask) {
                val task = Task(
                    id = UUID.randomUUID().toString(),
                    title = state.title.trim(),
                    description = state.description.trim(),
                    projectId = state.projectId,
                    sectionId = state.sectionId,
                    priority = state.priority,
                    dueDate = state.dueDate,
                    dueTime = state.dueTime,
                    recurrenceRule = state.recurrenceRule,
                    labels = selectedLabels,
                )
                val createdId = createTaskUseCase(task)

                // Create subtasks
                state.subtasks.forEach { subtask ->
                    createTaskUseCase(
                        subtask.copy(
                            projectId = state.projectId,
                            parentTaskId = createdId,
                        ),
                    )
                }
            } else {
                val updated = originalTask!!.copy(
                    title = state.title.trim(),
                    description = state.description.trim(),
                    projectId = state.projectId,
                    sectionId = state.sectionId,
                    priority = state.priority,
                    dueDate = state.dueDate,
                    dueTime = state.dueTime,
                    recurrenceRule = state.recurrenceRule,
                    labels = selectedLabels,
                )
                updateTaskUseCase(updated)

                // Handle subtask changes
                val existingSubtaskIds = originalTask!!.subtasks.map { it.id }.toSet()
                state.subtasks.forEach { subtask ->
                    if (subtask.id !in existingSubtaskIds) {
                        createTaskUseCase(
                            subtask.copy(
                                projectId = state.projectId,
                                parentTaskId = taskId,
                            ),
                        )
                    } else {
                        taskRepository.updateTask(subtask)
                    }
                }

                // Delete removed subtasks
                val currentSubtaskIds = state.subtasks.map { it.id }.toSet()
                existingSubtaskIds.filter { it !in currentSubtaskIds }.forEach { id ->
                    taskRepository.deleteTask(id)
                }
            }

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    fun delete() {
        if (taskId == null) return

        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}
