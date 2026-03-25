package com.prgramed.edoist.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val projectId: String,
    val sectionId: String? = null,
    val parentTaskId: String? = null,
    val priority: Priority = Priority.P4,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val dueTimezone: String? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val labels: List<Label> = emptyList(),
    val subtasks: List<Task> = emptyList(),
    val sortOrder: Int = 0,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val isOverdue: Boolean
        get() {
            val due = dueDate ?: return false
            if (isCompleted) return false
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return due < today
        }
}
