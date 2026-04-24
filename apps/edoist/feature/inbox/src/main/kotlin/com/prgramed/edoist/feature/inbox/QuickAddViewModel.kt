package com.prgramed.edoist.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.usecase.CreateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    projectRepository: ProjectRepository,
) : ViewModel() {
    val projects: StateFlow<List<Project>> = projectRepository.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createTask(title: String, projectId: String?, priority: Priority?, dueDate: LocalDate?) {
        viewModelScope.launch {
            val allProjects = projects.value
            val effectiveProjectId = projectId
                ?: allProjects.firstOrNull { !it.isInbox }?.id
                ?: allProjects.firstOrNull()?.id
                ?: ""
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = title,
                projectId = effectiveProjectId,
                priority = priority ?: Priority.P4,
                dueDate = dueDate,
            )
            createTaskUseCase(task)
        }
    }
}
