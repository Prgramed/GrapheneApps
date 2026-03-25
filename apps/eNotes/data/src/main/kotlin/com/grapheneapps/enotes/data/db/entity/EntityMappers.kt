package com.grapheneapps.enotes.data.db.entity

import com.grapheneapps.enotes.domain.model.Attachment
import com.grapheneapps.enotes.domain.model.Folder
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.model.NoteRevision
import com.grapheneapps.enotes.domain.model.SyncStatus

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    bodyJson = bodyJson,
    folderId = folderId,
    tags = tags.split(",").filter { it.isNotBlank() },
    isPinned = isPinned,
    isLocked = isLocked,
    encryptedBody = encryptedBody,
    createdAt = createdAt,
    editedAt = editedAt,
    deletedAt = deletedAt,
    syncStatus = try { SyncStatus.valueOf(syncStatus) } catch (_: Exception) { SyncStatus.LOCAL_ONLY },
    remoteEtag = remoteEtag,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    bodyJson = bodyJson,
    bodyText = extractPlainText(bodyJson),
    folderId = folderId,
    tags = tags.joinToString(","),
    isPinned = isPinned,
    isLocked = isLocked,
    encryptedBody = encryptedBody,
    createdAt = createdAt,
    editedAt = editedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus.name,
    remoteEtag = remoteEtag,
)

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    parentId = parentId,
    iconEmoji = iconEmoji,
    createdAt = createdAt,
    syncStatus = try { SyncStatus.valueOf(syncStatus) } catch (_: Exception) { SyncStatus.LOCAL_ONLY },
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    name = name,
    parentId = parentId,
    iconEmoji = iconEmoji,
    createdAt = createdAt,
    syncStatus = syncStatus.name,
)

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    noteId = noteId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localPath = localPath,
    remotePath = remotePath,
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    noteId = noteId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localPath = localPath,
    remotePath = remotePath,
)

fun NoteRevisionEntity.toDomain(): NoteRevision = NoteRevision(
    id = id,
    noteId = noteId,
    bodySnapshot = bodySnapshot,
    encryptedSnapshot = encryptedSnapshot,
    createdAt = createdAt,
    deltaChars = deltaChars,
)

fun NoteRevision.toEntity(): NoteRevisionEntity = NoteRevisionEntity(
    id = id,
    noteId = noteId,
    bodySnapshot = bodySnapshot,
    encryptedSnapshot = encryptedSnapshot,
    createdAt = createdAt,
    deltaChars = deltaChars,
)

private fun extractPlainText(bodyJson: String): String {
    if (bodyJson.isBlank()) return ""
    if (bodyJson.startsWith("[")) {
        // JSON block format — extract "text" fields
        return try {
            val arr = org.json.JSONArray(bodyJson)
            (0 until arr.length()).joinToString(" ") { i ->
                arr.getJSONObject(i).optString("text", "")
            }.trim().take(5000)
        } catch (_: Exception) {
            bodyJson.take(5000)
        }
    }
    // Raw markdown — strip markdown syntax
    return bodyJson
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("- \\[[ x]] ", RegexOption.MULTILINE), "")
        .replace(Regex("\\*{1,2}|_{1,2}|~~|`"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(5000)
}
