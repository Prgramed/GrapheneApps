package com.prgramed.edoist.navigation

object EDoistDestinations {
    const val TODAY = "today"
    const val INBOX = "inbox"
    const val UPCOMING = "upcoming"
    const val SEARCH = "search"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val TASK_DETAIL = "task/{taskId}"
    const val TASK_NEW = "task/new?projectId={projectId}&sectionId={sectionId}"
    const val SETTINGS = "settings"

    fun projectDetail(id: String) = "project/$id"
    fun taskDetail(id: String) = "task/$id"
    fun taskNew(projectId: String? = null, sectionId: String? = null) = buildString {
        append("task/new")
        val params = buildList {
            if (projectId != null) add("projectId=$projectId")
            if (sectionId != null) add("sectionId=$sectionId")
        }
        if (params.isNotEmpty()) {
            append("?")
            append(params.joinToString("&"))
        }
    }
}
