package com.prgramed.edoist.feature.taskdetail

import com.prgramed.edoist.domain.model.Label
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class TaskDetailUiState(
    val title: String = "",
    val description: String = "",
    val projectId: String = "",
    val sectionId: String? = null,
    val priority: Priority = Priority.P4,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val labels: List<Label> = emptyList(),
    val selectedLabelIds: Set<String> = emptySet(),
    val subtasks: List<Task> = emptyList(),
    val newSubtaskTitle: String = "",
    val availableProjects: List<Project> = emptyList(),
    val availableLabels: List<Label> = emptyList(),
    val isNewTask: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
)
