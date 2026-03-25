package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.Task
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
    operator fun invoke(projectId: String): Flow<ProjectWithTasks> =
        combine(
            projectRepository.observeProjectWithSections(projectId).filterNotNull(),
            taskRepository.getUnsectionedTasksByProject(projectId),
        ) { project, unsectionedTasks ->
            val sectionsWithTasks = project.sections.map { section ->
                section // Tasks are already populated via the repository
            }

            ProjectWithTasks(
                project = project,
                sections = sectionsWithTasks,
                unsectionedTasks = unsectionedTasks,
            )
        }
}
