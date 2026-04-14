package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ProjectWithTasks(
    val project: Project,
    val sections: List<Section>,
    val unsectionedTasks: List<Task>,
)

class GetProjectWithTasksUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(projectId: String, showCompleted: Boolean = false): Flow<ProjectWithTasks> =
        projectRepository.observeProjectWithSections(projectId)
            .filterNotNull()
            .flatMapLatest { project ->
                val unsectionedFlow = taskRepository.getUnsectionedTasksByProject(projectId, includeCompleted = showCompleted)

                if (project.sections.isEmpty()) {
                    unsectionedFlow.map { unsectioned ->
                        ProjectWithTasks(project, emptyList(), unsectioned)
                    }
                } else {
                    val sectionFlows = project.sections.map { section ->
                        taskRepository.getTasksBySection(section.id, includeCompleted = showCompleted)
                            .map { tasks -> section.copy(tasks = tasks) }
                    }

                    combine(listOf(unsectionedFlow) + sectionFlows) { results ->
                        val unsectioned = results[0] as List<Task>
                        val sections = results.drop(1).map { it as Section }
                        ProjectWithTasks(project, sections, unsectioned)
                    }
                }
            }
}
