package com.grapheneapps.enotes.data.joplin

sealed class JoplinItem {
    abstract val id: String

    data class Note(
        override val id: String,
        val title: String,
        val body: String,
        val parentId: String?,
        val createdTime: Long,
        val updatedTime: Long,
        val isTodo: Boolean = false,
        val todoCompleted: Boolean = false,
        val isEncrypted: Boolean = false,
    ) : JoplinItem()

    data class Notebook(
        override val id: String,
        val title: String,
        val parentId: String?,
    ) : JoplinItem()

    data class Tag(
        override val id: String,
        val title: String,
    ) : JoplinItem()

    data class NoteTag(
        override val id: String,
        val noteId: String,
        val tagId: String,
    ) : JoplinItem()
}

object JoplinParser {

    fun parse(content: String): JoplinItem? {
        // Split body from metadata footer
        // Joplin format: Markdown body, then blank line, then key: value metadata
        val lines = content.lines()
        val metadataStartIndex = findMetadataStart(lines)
        val body = lines.take(metadataStartIndex).joinToString("\n").trim()
        val metadata = parseMetadata(lines.drop(metadataStartIndex))

        val id = metadata["id"] ?: return null
        val type = metadata["type_"]?.toIntOrNull() ?: return null
        val title = metadata["title"] ?: body.lines().firstOrNull()?.take(100) ?: ""
        val parentId = metadata["parent_id"]?.ifBlank { null }

        return when (type) {
            1 -> { // Note
                val createdTime = parseJoplinTime(metadata["created_time"])
                val updatedTime = parseJoplinTime(metadata["updated_time"])
                val isTodo = metadata["is_todo"] == "1"
                val todoCompleted = metadata["todo_completed"]?.let { it != "0" && it.isNotBlank() } ?: false
                val isEncrypted = metadata["encryption_applied"] == "1"

                // Strip the title (first line) from the body — Joplin duplicates it
                val noteBody = body.lines().drop(1).joinToString("\n").trimStart()

                JoplinItem.Note(
                    id = id,
                    title = title,
                    body = noteBody,
                    parentId = parentId,
                    createdTime = createdTime,
                    updatedTime = updatedTime,
                    isTodo = isTodo,
                    todoCompleted = todoCompleted,
                    isEncrypted = isEncrypted,
                )
            }
            2 -> JoplinItem.Notebook(id = id, title = title, parentId = parentId) // Folder
            5 -> JoplinItem.Tag(id = id, title = title) // Tag
            6 -> { // NoteTag join
                val noteId = metadata["note_id"] ?: return null
                val tagId = metadata["tag_id"] ?: return null
                JoplinItem.NoteTag(id = id, noteId = noteId, tagId = tagId)
            }
            else -> null // Skip settings, resources, master keys, etc.
        }
    }

    private fun findMetadataStart(lines: List<String>): Int {
        // Metadata starts after the last blank line that's followed by key: value pairs
        for (i in lines.indices.reversed()) {
            if (lines[i].isBlank() && i + 1 < lines.size && lines[i + 1].contains(": ")) {
                return i + 1
            }
        }
        // If no blank line separator found, check from the end
        var metaStart = lines.size
        for (i in lines.indices.reversed()) {
            if (lines[i].matches(Regex("^[a-z_]+:.*"))) {
                metaStart = i
            } else {
                break
            }
        }
        return metaStart
    }

    private fun parseMetadata(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val colonIndex = line.indexOf(": ")
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 2).trim()
                map[key] = value
            }
        }
        return map
    }

    private fun parseJoplinTime(timeStr: String?): Long {
        if (timeStr == null) return System.currentTimeMillis()
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
