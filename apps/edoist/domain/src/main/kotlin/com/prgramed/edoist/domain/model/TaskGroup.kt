package com.prgramed.edoist.domain.model

import kotlinx.datetime.LocalDate

sealed class TaskGroup {

    abstract val tasks: List<Task>

    data class ByDate(
        val date: LocalDate,
        override val tasks: List<Task>,
    ) : TaskGroup()

    data class ByPriority(
        val priority: Priority,
        override val tasks: List<Task>,
    ) : TaskGroup()

    data class ByProject(
        val project: Project,
        override val tasks: List<Task>,
    ) : TaskGroup()

    data class ByLabel(
        val label: Label,
        override val tasks: List<Task>,
    ) : TaskGroup()

    data class BySection(
        val section: Section?,
        override val tasks: List<Task>,
    ) : TaskGroup()

    data class Overdue(
        override val tasks: List<Task>,
    ) : TaskGroup()
}
