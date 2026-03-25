package com.prgramed.edoist.feature.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.model.ViewType
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.usecase.CompleteTaskUseCase
import com.prgramed.edoist.domain.usecase.GetProjectWithTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getProjectWithTasksUseCase: GetProjectWithTasksUseCase,
    private val projectRepository: ProjectRepository,
    private val completeTaskUseCase: CompleteTaskUseCase,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])
    private val viewTypeFlow = MutableStateFlow(ViewType.LIST)
    private val collapsedSections = MutableStateFlow<Set<String>>(emptySet())

    val uiState = combine(
        getProjectWithTasksUseCase(projectId),
        viewTypeFlow,
        collapsedSections,
    ) { projectWithTasks, viewType, collapsed ->
        val sections = projectWithTasks.sections.map { section ->
            section.copy(isCollapsed = section.id in collapsed)
        }

        ProjectDetailUiState(
            project = projectWithTasks.project,
            unsectionedTasks = projectWithTasks.unsectionedTasks,
            sections = sections,
            viewType = projectWithTasks.project.defaultView.takeIf { viewType == ViewType.LIST && !viewTypeExplicitlySet }
                ?: viewType,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectDetailUiState(),
    )

    private var viewTypeExplicitlySet = false

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            completeTaskUseCase(taskId)
        }
    }

    fun switchView(viewType: ViewType) {
        viewTypeExplicitlySet = true
        viewTypeFlow.value = viewType
    }

    fun toggleSectionCollapsed(sectionId: String) {
        collapsedSections.value = collapsedSections.value.let { current ->
            if (sectionId in current) current - sectionId else current + sectionId
        }
    }

    fun createSection(name: String) {
        viewModelScope.launch {
            projectRepository.createSection(name = name, projectId = projectId)
        }
    }

    fun deleteSection(sectionId: String) {
        viewModelScope.launch {
            projectRepository.deleteSection(sectionId)
        }
    }
}
