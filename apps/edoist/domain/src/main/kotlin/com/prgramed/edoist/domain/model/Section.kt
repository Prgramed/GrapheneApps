package com.prgramed.edoist.domain.model

data class Section(
    val id: String,
    val name: String,
    val projectId: String,
    val sortOrder: Int = 0,
    val isCollapsed: Boolean = false,
    val tasks: List<Task> = emptyList(),
)
