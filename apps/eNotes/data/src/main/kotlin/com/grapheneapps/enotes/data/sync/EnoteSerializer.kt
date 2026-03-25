package com.grapheneapps.enotes.data.sync

import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.model.SyncStatus
import org.json.JSONArray
import org.json.JSONObject

object EnoteSerializer {

    fun toJson(note: Note, folderPath: String = "/"): String {
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("id", note.id)
        obj.put("title", note.title)
        obj.put("created_at", note.createdAt)
        obj.put("edited_at", note.editedAt)
        obj.put("folder_path", folderPath)
        obj.put("tags", JSONArray(note.tags))
        obj.put("is_pinned", note.isPinned)
        obj.put("is_locked", note.isLocked)
        if (note.isLocked && note.encryptedBody != null) {
            obj.put("body", JSONObject.NULL)
            obj.put("encrypted_body", note.encryptedBody)
        } else {
            obj.put("body", note.bodyJson)
            obj.put("encrypted_body", JSONObject.NULL)
        }
        return obj.toString(2)
    }

    fun fromJson(json: String): Note? {
        return try {
            val obj = JSONObject(json)
            val id = obj.getString("id")
            val title = obj.optString("title", "")
            val createdAt = obj.optLong("created_at", System.currentTimeMillis())
            val editedAt = obj.optLong("edited_at", System.currentTimeMillis())
            val tags = mutableListOf<String>()
            val tagsArr = obj.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tags.add(tagsArr.getString(i))
                }
            }
            val isPinned = obj.optBoolean("is_pinned", false)
            val isLocked = obj.optBoolean("is_locked", false)
            val body = if (obj.isNull("body")) "" else obj.optString("body", "")
            val encryptedBody = if (obj.isNull("encrypted_body")) null else obj.optString("encrypted_body")

            Note(
                id = id,
                title = title,
                bodyJson = body,
                tags = tags,
                isPinned = isPinned,
                isLocked = isLocked,
                encryptedBody = encryptedBody,
                createdAt = createdAt,
                editedAt = editedAt,
                syncStatus = SyncStatus.SYNCED,
            )
        } catch (e: Exception) {
            timber.log.Timber.w("Failed to parse .enote: ${e.message}")
            null
        }
    }
}
