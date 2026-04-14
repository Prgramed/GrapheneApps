package com.prgramed.edoist.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks locally-deleted item IDs so sync can propagate deletions to the server
 * instead of resurrecting them from the remote payload on the next merge.
 */
@Singleton
class DeletionTracker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("edoist_deletions", Context.MODE_PRIVATE)

    fun trackProjectDeletion(projectId: String) {
        val ids = getDeletedProjectIds().toMutableSet()
        ids.add(projectId)
        prefs.edit().putStringSet(KEY_PROJECTS, ids).apply()
    }

    fun trackTaskDeletion(taskId: String) {
        val ids = getDeletedTaskIds().toMutableSet()
        ids.add(taskId)
        prefs.edit().putStringSet(KEY_TASKS, ids).apply()
    }

    fun getDeletedProjectIds(): Set<String> =
        prefs.getStringSet(KEY_PROJECTS, emptySet()) ?: emptySet()

    fun getDeletedTaskIds(): Set<String> =
        prefs.getStringSet(KEY_TASKS, emptySet()) ?: emptySet()

    fun clearAfterSync() {
        prefs.edit()
            .remove(KEY_PROJECTS)
            .remove(KEY_TASKS)
            .apply()
    }

    private companion object {
        const val KEY_PROJECTS = "deleted_project_ids"
        const val KEY_TASKS = "deleted_task_ids"
    }
}
