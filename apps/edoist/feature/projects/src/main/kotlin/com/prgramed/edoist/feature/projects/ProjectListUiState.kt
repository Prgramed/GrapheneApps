package com.prgramed.edoist.feature.projects

import com.prgramed.edoist.domain.model.Label
import com.prgramed.edoist.domain.model.Project

data class ProjectListUiState(
    val projects: List<Project> = emptyList(),
    val labels: List<Label> = emptyList(),
    val isLoading: Boolean = true,
)
