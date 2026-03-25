package com.prgramed.edoist.feature.projects

import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.model.ViewType

data class ProjectDetailUiState(
    val project: Project? = null,
    val unsectionedTasks: List<Task> = emptyList(),
    val sections: List<Section> = emptyList(),
    val viewType: ViewType = ViewType.LIST,
    val isLoading: Boolean = true,
)
