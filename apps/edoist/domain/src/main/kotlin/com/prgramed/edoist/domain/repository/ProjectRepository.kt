package com.prgramed.edoist.domain.repository

import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.ViewType
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun observeAllActive(): Flow<List<Project>>

    fun observeProjectWithSections(projectId: String): Flow<Project?>

    suspend fun getInboxProject(): Project

    suspend fun createProject(
        name: String,
        color: Long,
        iconName: String? = null,
        defaultView: ViewType = ViewType.LIST,
    ): String

    suspend fun updateProject(project: Project)

    suspend fun archiveProject(projectId: String)

    suspend fun deleteProject(projectId: String)

    suspend fun updateProjectSortOrder(projectId: String, sortOrder: Int)

    suspend fun createSection(name: String, projectId: String): String

    suspend fun updateSection(section: Section)

    suspend fun deleteSection(sectionId: String)

    suspend fun toggleSectionCollapsed(sectionId: String)

    suspend fun updateSectionSortOrder(sectionId: String, sortOrder: Int)
}
