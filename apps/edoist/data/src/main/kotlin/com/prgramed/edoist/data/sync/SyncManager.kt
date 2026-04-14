package com.prgramed.edoist.data.sync

import androidx.room.withTransaction
import com.prgramed.edoist.data.database.EDoistDatabase
import com.prgramed.edoist.data.database.entity.LabelEntity
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.entity.SectionEntity
import com.prgramed.edoist.data.database.entity.SyncMetadataEntity
import com.prgramed.edoist.data.database.entity.TaskEntity
import com.prgramed.edoist.data.database.entity.TaskLabelCrossRef
import com.prgramed.edoist.data.repository.UserPreferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncResult {
    SUCCESS,
    FAILED,
    NO_CONFIG,
}

@Singleton
class SyncManager @Inject constructor(
    private val webDavClient: WebDavClient,
    private val database: EDoistDatabase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val deletionTracker: DeletionTracker,
) {
    suspend fun sync(): SyncResult {
        val prefs = userPreferencesDataStore.getPreferences().first()
        val url = prefs.webDavUrl
        val username = prefs.username
        val password = prefs.passwordEncrypted

        if (url.isBlank() || username.isBlank()) {
            return SyncResult.NO_CONFIG
        }

        return try {
            android.util.Log.d("SyncManager", "Syncing to $url/$WEBDAV_PATH as $username")
            val localPayload = buildLocalPayload()
            android.util.Log.d("SyncManager", "Local payload: ${localPayload.tasks.size} tasks, ${localPayload.projects.size} projects")

            // Collect locally-tracked deletions to exclude from merge and propagate to server
            val localDeletedProjects = deletionTracker.getDeletedProjectIds()
            val localDeletedTasks = deletionTracker.getDeletedTaskIds()

            // Use stored ETag to skip download if server data unchanged
            val lastSync = database.syncMetadataDao().get()
            val downloadResult = webDavClient.download(
                url = url,
                username = username,
                password = password,
                path = WEBDAV_PATH,
                ifNoneMatch = lastSync?.serverEtag,
            )

            val serverBytes = downloadResult.bytes
            val serverHadData = downloadResult.bytes != null || downloadResult.notModified

            var mergedPayload: SyncPayload = if (serverBytes != null) {
                val serverPayload = SyncPayload.fromJson(String(serverBytes))
                if (serverPayload != null) {
                    // Union both sides' deletion lists, remove deleted items from merged content
                    val allDeletedProjects = localDeletedProjects + serverPayload.deletedProjectIds
                    val allDeletedTasks = localDeletedTasks + serverPayload.deletedTaskIds
                    mergePayloads(localPayload, serverPayload).let { merged ->
                        merged.copy(
                            projects = merged.projects.filter { it.id !in allDeletedProjects },
                            tasks = merged.tasks.filter { it.id !in allDeletedTasks },
                            deletedProjectIds = allDeletedProjects.toList(),
                            deletedTaskIds = allDeletedTasks.toList(),
                        )
                    }
                } else {
                    localPayload
                }
            } else {
                localPayload
            }

            // Include local-only deletions in the payload so other devices honor them
            if (localDeletedProjects.isNotEmpty() || localDeletedTasks.isNotEmpty()) {
                mergedPayload = mergedPayload.copy(
                    deletedProjectIds = (mergedPayload.deletedProjectIds + localDeletedProjects).distinct(),
                    deletedTaskIds = (mergedPayload.deletedTaskIds + localDeletedTasks).distinct(),
                )
            }

            // Always upload the merged payload to keep server in sync
            val uploaded = if (mergedPayload.tasks.isNotEmpty() || mergedPayload.projects.isNotEmpty() || !serverHadData) {
                webDavClient.upload(
                    url = url,
                    username = username,
                    password = password,
                    path = WEBDAV_PATH,
                    data = mergedPayload.toJson().toByteArray(),
                )
            } else true // No changes to upload

            if (!uploaded) return SyncResult.FAILED

            // Apply merged data locally if we merged with server
            // Guard: never apply an empty payload — it means server data was lost/corrupt
            if (serverBytes != null && mergedPayload.tasks.isNotEmpty()) {
                applyPayloadLocally(mergedPayload)
            }

            // Clear local deletion records after successful sync — server now has them
            deletionTracker.clearAfterSync()

            database.syncMetadataDao().upsert(
                SyncMetadataEntity(
                    id = "singleton",
                    lastSyncMillis = System.currentTimeMillis(),
                    lastSyncStatus = "SUCCESS",
                    pendingChangesCount = 0,
                    serverEtag = downloadResult.etag ?: lastSync?.serverEtag,
                ),
            )

            SyncResult.SUCCESS
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Sync failed", e)
            try {
                database.syncMetadataDao().upsert(
                    SyncMetadataEntity(
                        id = "singleton",
                        lastSyncMillis = System.currentTimeMillis(),
                        lastSyncStatus = "FAILED",
                    ),
                )
            } catch (_: Exception) {
                // Best effort
            }
            SyncResult.FAILED
        }
    }

    private suspend fun buildLocalPayload(): SyncPayload {
        val taskDao = database.taskDao()
        val projectDao = database.projectDao()
        val sectionDao = database.sectionDao()
        val labelDao = database.labelDao()

        // Always send ALL tasks — delta sync is unsafe because merge-by-ID
        // needs complete data from both sides to produce a correct result.
        val allTasks = taskDao.getTasksModifiedSince(0L)
        val allProjects = projectDao.getAllActive()
        val allLabels = labelDao.getAll()

        val taskSyncItems = allTasks.map { task ->
            TaskSyncItem(
                id = task.id,
                title = task.title,
                description = task.description,
                projectId = task.projectId,
                sectionId = task.sectionId,
                parentTaskId = task.parentTaskId,
                priority = task.priority,
                dueDateEpochDay = task.dueDateEpochDay,
                dueTimeMinuteOfDay = task.dueTimeMinuteOfDay,
                dueTimezone = task.dueTimezone,
                recurrenceRule = task.recurrenceRule,
                isCompleted = task.isCompleted,
                completedAtMillis = task.completedAtMillis,
                sortOrder = task.sortOrder,
                createdAtMillis = task.createdAtMillis,
                updatedAtMillis = task.updatedAtMillis,
                labelIds = taskDao.getLabelIdsForTask(task.id),
            )
        }

        val projectSyncItems = allProjects.map { project ->
            ProjectSyncItem(
                id = project.id,
                name = project.name,
                color = project.color,
                iconName = project.iconName,
                isInbox = project.isInbox,
                isArchived = project.isArchived,
                defaultView = project.defaultView,
                sortOrder = project.sortOrder,
                createdAtMillis = project.createdAtMillis,
                updatedAtMillis = project.updatedAtMillis,
            )
        }

        // Gather sections for each project
        val allSections = mutableListOf<SectionEntity>()
        allProjects.forEach { project ->
            allSections.addAll(sectionDao.getByProjectId(project.id))
        }

        val sectionSyncItems = allSections.map { section ->
            SectionSyncItem(
                id = section.id,
                name = section.name,
                projectId = section.projectId,
                sortOrder = section.sortOrder,
                isCollapsed = section.isCollapsed,
                createdAtMillis = section.createdAtMillis,
            )
        }

        val labelSyncItems = allLabels.map { label ->
            LabelSyncItem(
                id = label.id,
                name = label.name,
                color = label.color,
                sortOrder = label.sortOrder,
            )
        }

        val maxUpdated = maxOf(
            allTasks.maxOfOrNull { it.updatedAtMillis } ?: 0L,
            allProjects.maxOfOrNull { it.updatedAtMillis } ?: 0L,
            System.currentTimeMillis(),
        )

        return SyncPayload(
            tasks = taskSyncItems,
            projects = projectSyncItems,
            sections = sectionSyncItems,
            labels = labelSyncItems,
            lastModifiedMillis = maxUpdated,
        )
    }

    private fun mergePayloads(local: SyncPayload, server: SyncPayload): SyncPayload {
        val mergedTasks = mergeById(local.tasks, server.tasks, { it.id }, { it.updatedAtMillis })
        val mergedProjects = mergeById(local.projects, server.projects, { it.id }, { it.updatedAtMillis })
        val mergedSections = mergeById(local.sections, server.sections, { it.id }, { it.createdAtMillis })
        val mergedLabels = mergeById(local.labels, server.labels, { it.id }, { it.sortOrder.toLong() })

        return SyncPayload(
            tasks = mergedTasks,
            projects = mergedProjects,
            sections = mergedSections,
            labels = mergedLabels,
            lastModifiedMillis = System.currentTimeMillis(),
        )
    }

    private fun <T> mergeById(
        local: List<T>,
        server: List<T>,
        getId: (T) -> String,
        getTimestamp: (T) -> Long,
    ): List<T> {
        val localMap = local.associateBy(getId)
        val serverMap = server.associateBy(getId)
        val allIds = localMap.keys + serverMap.keys

        return allIds.map { id ->
            val localItem = localMap[id]
            val serverItem = serverMap[id]

            when {
                localItem == null -> serverItem!!
                serverItem == null -> localItem
                getTimestamp(localItem) >= getTimestamp(serverItem) -> localItem
                else -> serverItem
            }
        }
    }

    private suspend fun applyPayloadLocally(payload: SyncPayload) {
        database.withTransaction {
            val taskDao = database.taskDao()
            val projectDao = database.projectDao()
            val labelDao = database.labelDao()

            // Apply projects first (foreign key dependency)
            payload.projects.forEach { project ->
                projectDao.insert(
                    ProjectEntity(
                        id = project.id,
                        name = project.name,
                        color = project.color,
                        iconName = project.iconName,
                        isInbox = project.isInbox,
                        isArchived = project.isArchived,
                        defaultView = project.defaultView,
                        sortOrder = project.sortOrder,
                        createdAtMillis = project.createdAtMillis,
                        updatedAtMillis = project.updatedAtMillis,
                    ),
                )
            }

            // Apply sections
            payload.sections.forEach { section ->
                database.sectionDao().insert(
                    SectionEntity(
                        id = section.id,
                        name = section.name,
                        projectId = section.projectId,
                        sortOrder = section.sortOrder,
                        isCollapsed = section.isCollapsed,
                        createdAtMillis = section.createdAtMillis,
                    ),
                )
            }

            // Apply labels
            payload.labels.forEach { label ->
                labelDao.insert(
                    LabelEntity(
                        id = label.id,
                        name = label.name,
                        color = label.color,
                        sortOrder = label.sortOrder,
                    ),
                )
            }

            // Apply tasks
            payload.tasks.forEach { task ->
                taskDao.insert(
                    TaskEntity(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        projectId = task.projectId,
                        sectionId = task.sectionId,
                        parentTaskId = task.parentTaskId,
                        priority = task.priority,
                        dueDateEpochDay = task.dueDateEpochDay,
                        dueTimeMinuteOfDay = task.dueTimeMinuteOfDay,
                        dueTimezone = task.dueTimezone,
                        recurrenceRule = task.recurrenceRule,
                        isCompleted = task.isCompleted,
                        completedAtMillis = task.completedAtMillis,
                        sortOrder = task.sortOrder,
                        createdAtMillis = task.createdAtMillis,
                        updatedAtMillis = task.updatedAtMillis,
                    ),
                )

                // Apply label cross refs
                taskDao.deleteLabelsForTask(task.id)
                task.labelIds.forEach { labelId ->
                    taskDao.insertTaskLabelCrossRef(
                        TaskLabelCrossRef(taskId = task.id, labelId = labelId),
                    )
                }
            }
        }
    }

    private companion object {
        const val WEBDAV_PATH = "data.json"
    }
}
