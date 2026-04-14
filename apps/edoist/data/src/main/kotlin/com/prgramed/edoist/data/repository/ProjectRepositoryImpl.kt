package com.prgramed.edoist.data.repository

import com.prgramed.edoist.data.database.dao.ProjectDao
import com.prgramed.edoist.data.database.dao.SectionDao
import com.prgramed.edoist.data.database.dao.TaskDao
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.entity.SectionEntity
import com.prgramed.edoist.data.sync.DeletionTracker
import com.prgramed.edoist.domain.model.Project
import com.prgramed.edoist.domain.model.Section
import com.prgramed.edoist.domain.model.ViewType
import com.prgramed.edoist.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val sectionDao: SectionDao,
    private val taskDao: TaskDao,
    private val deletionTracker: DeletionTracker,
) : ProjectRepository {

    // ── Mapping ────────────────────────────────────────────────────────────

    private fun ProjectEntity.toDomain(activeTaskCount: Int = 0): Project = Project(
        id = id,
        name = name,
        color = color,
        iconName = iconName.ifEmpty { null },
        isInbox = isInbox,
        isArchived = isArchived,
        defaultView = runCatching { ViewType.valueOf(defaultView) }.getOrDefault(ViewType.LIST),
        sortOrder = sortOrder,
        activeTaskCount = activeTaskCount,
    )

    private fun SectionEntity.toDomain(): Section = Section(
        id = id,
        name = name,
        projectId = projectId,
        sortOrder = sortOrder,
        isCollapsed = isCollapsed,
    )

    private fun Project.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        color = color,
        iconName = iconName ?: "",
        isInbox = isInbox,
        isArchived = isArchived,
        defaultView = defaultView.name,
        sortOrder = sortOrder,
        createdAtMillis = 0,
        updatedAtMillis = System.currentTimeMillis(),
    )

    // ── Flow queries ───────────────────────────────────────────────────────

    override fun observeAllActive(): Flow<List<Project>> =
        projectDao.observeAllActiveWithTaskCount().map { results ->
            results.map { it.project.toDomain(activeTaskCount = it.taskCount) }
        }

    override fun observeProjectWithSections(projectId: String): Flow<Project?> =
        combine(
            projectDao.observeProjectWithSections(projectId),
            taskDao.getActiveTaskCount(projectId),
        ) { projectWithSections, taskCount ->
            projectWithSections?.let { pws ->
                pws.project.toDomain(activeTaskCount = taskCount).copy(
                    sections = pws.sections
                        .sortedBy { it.sortOrder }
                        .map { it.toDomain() },
                )
            }
        }

    override suspend fun getInboxProject(): Project {
        val entity = projectDao.getInboxProject()
            ?: error("Inbox project not found — database may not be initialized")
        return entity.toDomain()
    }

    // ── Project mutations ──────────────────────────────────────────────────

    override suspend fun createProject(
        name: String,
        color: Long,
        iconName: String?,
        defaultView: ViewType,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val allActive = projectDao.getAllActive()
        val maxSortOrder = allActive.maxOfOrNull { it.sortOrder } ?: 0
        projectDao.insert(
            ProjectEntity(
                id = id,
                name = name,
                color = color,
                iconName = iconName ?: "",
                isInbox = false,
                isArchived = false,
                defaultView = defaultView.name,
                sortOrder = maxSortOrder + 1,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        return id
    }

    override suspend fun updateProject(project: Project) {
        val existing = projectDao.getById(project.id) ?: return
        projectDao.update(
            existing.copy(
                name = project.name,
                color = project.color,
                iconName = project.iconName ?: "",
                defaultView = project.defaultView.name,
                sortOrder = project.sortOrder,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun archiveProject(projectId: String) {
        val existing = projectDao.getById(projectId) ?: return
        projectDao.update(
            existing.copy(
                isArchived = true,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteProject(projectId: String) {
        val existing = projectDao.getById(projectId) ?: return
        deletionTracker.trackProjectDeletion(projectId)
        projectDao.delete(existing)
    }

    override suspend fun updateProjectSortOrder(projectId: String, sortOrder: Int) {
        projectDao.updateSortOrder(projectId, sortOrder, System.currentTimeMillis())
    }

    // ── Section mutations ──────────────────────────────────────────────────

    override suspend fun createSection(name: String, projectId: String): String {
        val id = UUID.randomUUID().toString()
        val sections = sectionDao.observeByProject(projectId).first()
        val maxSortOrder = sections.maxOfOrNull { it.sortOrder } ?: 0
        sectionDao.insert(
            SectionEntity(
                id = id,
                name = name,
                projectId = projectId,
                sortOrder = maxSortOrder + 1,
                isCollapsed = false,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        return id
    }

    override suspend fun updateSection(section: Section) {
        val existing = sectionDao.getById(section.id) ?: return
        sectionDao.update(
            existing.copy(
                name = section.name,
                sortOrder = section.sortOrder,
                isCollapsed = section.isCollapsed,
            ),
        )
    }

    override suspend fun deleteSection(sectionId: String) {
        val existing = sectionDao.getById(sectionId) ?: return
        sectionDao.delete(existing)
    }

    override suspend fun toggleSectionCollapsed(sectionId: String) {
        val existing = sectionDao.getById(sectionId) ?: return
        sectionDao.setCollapsed(sectionId, !existing.isCollapsed)
    }

    override suspend fun updateSectionSortOrder(sectionId: String, sortOrder: Int) {
        sectionDao.updateSortOrder(sectionId, sortOrder)
    }
}
