package com.prgramed.edoist.data.importer

import com.prgramed.edoist.data.database.EDoistDatabase
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.entity.SectionEntity
import com.prgramed.edoist.data.database.entity.TaskEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val projectsImported: Int,
    val tasksImported: Int,
    val sectionsImported: Int,
    val errors: List<String> = emptyList(),
)

@Singleton
class TodoistCsvImporter @Inject constructor(
    private val database: EDoistDatabase,
) {

    suspend fun importFromZip(inputStream: InputStream): ImportResult {
        var projectsImported = 0
        var tasksImported = 0
        var sectionsImported = 0
        val errors = mutableListOf<String>()

        try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                // Skip macOS resource forks and hidden files
                if (!entry.isDirectory && name.endsWith(".csv") &&
                    !name.contains("__MACOSX") && !name.substringAfterLast('/').startsWith(".")
                ) {
                    val projectName = name
                        .substringAfterLast('/')
                        .removeSuffix(".csv")

                    try {
                        val csvContent = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                            .readText()
                        val result = importCsvProject(projectName, csvContent)
                        projectsImported++
                        tasksImported += result.first
                        sectionsImported += result.second
                    } catch (e: Exception) {
                        errors.add("Failed to import $projectName: ${e.message}")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
        } catch (e: Exception) {
            errors.add("Failed to read ZIP: ${e.message}")
        }

        return ImportResult(projectsImported, tasksImported, sectionsImported, errors)
    }

    suspend fun importFromCsv(projectName: String, inputStream: InputStream): ImportResult {
        return try {
            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            val (tasks, sections) = importCsvProject(projectName, content)
            ImportResult(1, tasks, sections)
        } catch (e: Exception) {
            ImportResult(0, 0, 0, listOf("Failed: ${e.message}"))
        }
    }

    private suspend fun importCsvProject(projectName: String, csvContent: String): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val projectId = UUID.randomUUID().toString()

        // Check if project already exists (by name)
        val existingProjects = database.projectDao().getAllActive()
        val isInbox = projectName.equals("Inbox", ignoreCase = true)
        val actualProjectId = if (isInbox) {
            existingProjects.find { it.isInbox }?.id ?: projectId
        } else {
            projectId
        }

        // Create project if not inbox
        if (!isInbox) {
            val sortOrder = existingProjects.size
            database.projectDao().insert(
                ProjectEntity(
                    id = actualProjectId,
                    name = projectName,
                    color = PROJECT_COLORS[projectName.hashCode().mod(PROJECT_COLORS.size)],
                    iconName = "",
                    isInbox = false,
                    isArchived = false,
                    sortOrder = sortOrder,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }

        // Parse CSV — strip BOM if present
        val cleanContent = csvContent.trimStart('\uFEFF')
        val lines = parseCsv(cleanContent)
        if (lines.isEmpty()) return Pair(0, 0)

        // First line is header
        val header = lines.first()
        val colMap = header.mapIndexed { index, name -> name.uppercase().trim() to index }.toMap()

        val typeCol = colMap["TYPE"] ?: return Pair(0, 0)
        val contentCol = colMap["CONTENT"] ?: return Pair(0, 0)
        val descCol = colMap["DESCRIPTION"]
        val priorityCol = colMap["PRIORITY"]
        val indentCol = colMap["INDENT"]
        val dateCol = colMap["DATE"]
        val deadlineCol = colMap["DEADLINE"]

        var tasksImported = 0
        var sectionsImported = 0

        // Track parent task IDs by indent level
        val parentStack = mutableListOf<String>() // stack of task IDs by indent
        var currentSectionId: String? = null

        for (i in 1 until lines.size) {
            val row = lines[i]
            if (row.size <= typeCol || row.size <= contentCol) continue

            val type = row[typeCol].trim().lowercase()
            val content = row[contentCol].trim()
            if (content.isBlank() || type == "meta" || type.isBlank()) continue

            when (type) {
                "section" -> {
                    val sectionId = UUID.randomUUID().toString()
                    database.sectionDao().insert(
                        SectionEntity(
                            id = sectionId,
                            name = content,
                            projectId = actualProjectId,
                            sortOrder = sectionsImported,
                            isCollapsed = false,
                            createdAtMillis = now,
                        ),
                    )
                    currentSectionId = sectionId
                    sectionsImported++
                    parentStack.clear()
                }

                "task" -> {
                    val taskId = UUID.randomUUID().toString()
                    val indent = indentCol?.let { row.getOrNull(it)?.trim()?.toIntOrNull() } ?: 1
                    val priority = priorityCol?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }
                        ?.let { mapTodoistPriority(it) } ?: 4
                    val description = descCol?.let { row.getOrNull(it)?.trim() } ?: ""
                    val dateStr = deadlineCol?.let { row.getOrNull(it)?.trim() }
                        ?: dateCol?.let { row.getOrNull(it)?.trim() }

                    // Determine parent based on indent
                    val parentTaskId = if (indent > 1 && parentStack.size >= indent - 1) {
                        parentStack[indent - 2]
                    } else null

                    // Parse date (Todoist uses various formats)
                    val dueDateEpochDay = dateStr?.let { parseTodoistDate(it) }

                    database.taskDao().insert(
                        TaskEntity(
                            id = taskId,
                            title = content,
                            description = description,
                            projectId = actualProjectId,
                            sectionId = if (indent == 1) currentSectionId else null,
                            parentTaskId = parentTaskId,
                            priority = priority,
                            dueDateEpochDay = dueDateEpochDay,
                            sortOrder = tasksImported,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        ),
                    )

                    // Update parent stack
                    while (parentStack.size >= indent) {
                        parentStack.removeAt(parentStack.lastIndex)
                    }
                    parentStack.add(taskId)

                    tasksImported++
                }

                "note" -> {
                    // Append note to the last task's description
                    if (parentStack.isNotEmpty()) {
                        val lastTaskId = parentStack.last()
                        val task = database.taskDao().getTaskById(lastTaskId)
                        if (task != null) {
                            val updatedDesc = if (task.description.isBlank()) {
                                content
                            } else {
                                "${task.description}\n$content"
                            }
                            database.taskDao().update(task.copy(description = updatedDesc))
                        }
                    }
                }
            }
        }

        return Pair(tasksImported, sectionsImported)
    }

    private fun parseCsv(content: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val lines = content.lines()

        for (line in lines) {
            if (line.isBlank()) continue
            result.add(parseCsvLine(line))
        }

        return result
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' && !inQuotes -> inQuotes = true
                char == '"' && inQuotes -> inQuotes = false
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())

        return fields
    }

    private fun mapTodoistPriority(todoistPriority: Int): Int {
        // Todoist: p1=4(highest), p2=3, p3=2, p4=1(lowest)
        // eDoist: 1=P1(highest), 2=P2, 3=P3, 4=P4(lowest)
        return when (todoistPriority) {
            4 -> 1  // Todoist p1 → eDoist P1
            3 -> 2  // Todoist p2 → eDoist P2
            2 -> 3  // Todoist p3 → eDoist P3
            else -> 4  // Todoist p4 → eDoist P4
        }
    }

    private fun parseTodoistDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        return try {
            // Try ISO format: 2026-03-16 or 2026-03-16T15:00:00
            val datePart = dateStr.substringBefore('T').substringBefore(' ')
            val parts = datePart.split('-')
            if (parts.size == 3) {
                val date = kotlinx.datetime.LocalDate(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                )
                date.toEpochDays().toLong()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val PROJECT_COLORS = listOf(
            0xFFDB4C3F, // Red
            0xFFFF9933, // Orange
            0xFFFAD000, // Yellow
            0xFF7ECC49, // Green
            0xFF299438, // Dark green
            0xFF6ACCBC, // Teal
            0xFF158FAD, // Blue
            0xFF14AAF5, // Light blue
            0xFF96C3EB, // Lavender
            0xFFB8B8B8, // Gray
            0xFFAF38EB, // Purple
            0xFFEB96EB, // Pink
        )
    }
}
