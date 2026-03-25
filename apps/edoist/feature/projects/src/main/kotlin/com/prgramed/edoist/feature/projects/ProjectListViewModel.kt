package com.prgramed.edoist.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.model.ViewType
import com.prgramed.edoist.domain.repository.LabelRepository
import com.prgramed.edoist.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val labelRepository: LabelRepository,
) : ViewModel() {

    val uiState = combine(
        projectRepository.observeAllActive(),
        labelRepository.observeAll(),
    ) { projects, labels ->
        ProjectListUiState(
            projects = projects,
            labels = labels,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectListUiState(),
    )

    fun createProject(name: String, color: Long, defaultView: ViewType) {
        viewModelScope.launch {
            projectRepository.createProject(
                name = name,
                color = color,
                defaultView = defaultView,
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    fun updateProject(
        projectId: String,
        name: String,
        color: Long,
        defaultView: ViewType,
    ) {
        viewModelScope.launch {
            val current = uiState.value.projects.find { it.id == projectId } ?: return@launch
            projectRepository.updateProject(
                current.copy(
                    name = name,
                    color = color,
                    defaultView = defaultView,
                ),
            )
        }
    }
}
