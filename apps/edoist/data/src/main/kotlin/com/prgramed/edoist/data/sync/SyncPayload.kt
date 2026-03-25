package com.prgramed.edoist.data.sync

import org.json.JSONArray
import org.json.JSONObject

data class SyncPayload(
    val tasks: List<TaskSyncItem>,
    val projects: List<ProjectSyncItem>,
    val sections: List<SectionSyncItem>,
    val labels: List<LabelSyncItem>,
    val lastModifiedMillis: Long,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("lastModifiedMillis", lastModifiedMillis)

        val tasksArray = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("title", task.title)
            obj.put("description", task.description)
            obj.put("projectId", task.projectId)
            obj.putOpt("sectionId", task.sectionId)
            obj.putOpt("parentTaskId", task.parentTaskId)
            obj.put("priority", task.priority)
            obj.putOpt("dueDateEpochDay", task.dueDateEpochDay)
            obj.putOpt("dueTimeMinuteOfDay", task.dueTimeMinuteOfDay)
            obj.putOpt("dueTimezone", task.dueTimezone)
            obj.putOpt("recurrenceRule", task.recurrenceRule)
            obj.put("isCompleted", task.isCompleted)
            obj.putOpt("completedAtMillis", task.completedAtMillis)
            obj.put("sortOrder", task.sortOrder)
            obj.put("createdAtMillis", task.createdAtMillis)
            obj.put("updatedAtMillis", task.updatedAtMillis)

            val labelIdsArray = JSONArray()
            task.labelIds.forEach { labelIdsArray.put(it) }
            obj.put("labelIds", labelIdsArray)

            tasksArray.put(obj)
        }
        json.put("tasks", tasksArray)

        val projectsArray = JSONArray()
        projects.forEach { project ->
            val obj = JSONObject()
            obj.put("id", project.id)
            obj.put("name", project.name)
            obj.put("color", project.color)
            obj.put("iconName", project.iconName)
            obj.put("isInbox", project.isInbox)
            obj.put("isArchived", project.isArchived)
            obj.put("defaultView", project.defaultView)
            obj.put("sortOrder", project.sortOrder)
            obj.put("createdAtMillis", project.createdAtMillis)
            obj.put("updatedAtMillis", project.updatedAtMillis)
            projectsArray.put(obj)
        }
        json.put("projects", projectsArray)

        val sectionsArray = JSONArray()
        sections.forEach { section ->
            val obj = JSONObject()
            obj.put("id", section.id)
            obj.put("name", section.name)
            obj.put("projectId", section.projectId)
            obj.put("sortOrder", section.sortOrder)
            obj.put("isCollapsed", section.isCollapsed)
            obj.put("createdAtMillis", section.createdAtMillis)
            sectionsArray.put(obj)
        }
        json.put("sections", sectionsArray)

        val labelsArray = JSONArray()
        labels.forEach { label ->
            val obj = JSONObject()
            obj.put("id", label.id)
            obj.put("name", label.name)
            obj.put("color", label.color)
            obj.put("sortOrder", label.sortOrder)
            labelsArray.put(obj)
        }
        json.put("labels", labelsArray)

        return json.toString(2)
    }

    companion object {
        fun fromJson(json: String): SyncPayload? = try {
            val root = JSONObject(json)
            val lastModifiedMillis = root.getLong("lastModifiedMillis")

            val tasks = mutableListOf<TaskSyncItem>()
            val tasksArray = root.getJSONArray("tasks")
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val labelIds = mutableListOf<String>()
                if (obj.has("labelIds")) {
                    val labelIdsArray = obj.getJSONArray("labelIds")
                    for (j in 0 until labelIdsArray.length()) {
                        labelIds.add(labelIdsArray.getString(j))
                    }
                }
                tasks.add(
                    TaskSyncItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        projectId = obj.getString("projectId"),
                        sectionId = obj.optStringOrNull("sectionId"),
                        parentTaskId = obj.optStringOrNull("parentTaskId"),
                        priority = obj.getInt("priority"),
                        dueDateEpochDay = obj.optLongOrNull("dueDateEpochDay"),
                        dueTimeMinuteOfDay = obj.optIntOrNull("dueTimeMinuteOfDay"),
                        dueTimezone = obj.optStringOrNull("dueTimezone"),
                        recurrenceRule = obj.optStringOrNull("recurrenceRule"),
                        isCompleted = obj.getBoolean("isCompleted"),
                        completedAtMillis = obj.optLongOrNull("completedAtMillis"),
                        sortOrder = obj.getInt("sortOrder"),
                        createdAtMillis = obj.getLong("createdAtMillis"),
                        updatedAtMillis = obj.getLong("updatedAtMillis"),
                        labelIds = labelIds,
                    ),
                )
            }

            val projects = mutableListOf<ProjectSyncItem>()
            val projectsArray = root.getJSONArray("projects")
            for (i in 0 until projectsArray.length()) {
                val obj = projectsArray.getJSONObject(i)
                projects.add(
                    ProjectSyncItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        color = obj.getLong("color"),
                        iconName = obj.optString("iconName", ""),
                        isInbox = obj.getBoolean("isInbox"),
                        isArchived = obj.getBoolean("isArchived"),
                        defaultView = obj.optString("defaultView", "LIST"),
                        sortOrder = obj.getInt("sortOrder"),
                        createdAtMillis = obj.getLong("createdAtMillis"),
                        updatedAtMillis = obj.getLong("updatedAtMillis"),
                    ),
                )
            }

            val sections = mutableListOf<SectionSyncItem>()
            val sectionsArray = root.getJSONArray("sections")
            for (i in 0 until sectionsArray.length()) {
                val obj = sectionsArray.getJSONObject(i)
                sections.add(
                    SectionSyncItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        projectId = obj.getString("projectId"),
                        sortOrder = obj.getInt("sortOrder"),
                        isCollapsed = obj.getBoolean("isCollapsed"),
                        createdAtMillis = obj.getLong("createdAtMillis"),
                    ),
                )
            }

            val labels = mutableListOf<LabelSyncItem>()
            val labelsArray = root.getJSONArray("labels")
            for (i in 0 until labelsArray.length()) {
                val obj = labelsArray.getJSONObject(i)
                labels.add(
                    LabelSyncItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        color = obj.getLong("color"),
                        sortOrder = obj.getInt("sortOrder"),
                    ),
                )
            }

            SyncPayload(
                tasks = tasks,
                projects = projects,
                sections = sections,
                labels = labels,
                lastModifiedMillis = lastModifiedMillis,
            )
        } catch (_: Exception) {
            null
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key) else null

        private fun JSONObject.optLongOrNull(key: String): Long? =
            if (has(key) && !isNull(key)) getLong(key) else null

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (has(key) && !isNull(key)) getInt(key) else null
    }
}

data class TaskSyncItem(
    val id: String,
    val title: String,
    val description: String,
    val projectId: String,
    val sectionId: String?,
    val parentTaskId: String?,
    val priority: Int,
    val dueDateEpochDay: Long?,
    val dueTimeMinuteOfDay: Int?,
    val dueTimezone: String?,
    val recurrenceRule: String?,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val labelIds: List<String>,
)

data class ProjectSyncItem(
    val id: String,
    val name: String,
    val color: Long,
    val iconName: String,
    val isInbox: Boolean,
    val isArchived: Boolean,
    val defaultView: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class SectionSyncItem(
    val id: String,
    val name: String,
    val projectId: String,
    val sortOrder: Int,
    val isCollapsed: Boolean,
    val createdAtMillis: Long,
)

data class LabelSyncItem(
    val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int,
)
