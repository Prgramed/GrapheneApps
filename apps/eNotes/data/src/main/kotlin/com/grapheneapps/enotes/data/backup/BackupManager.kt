package com.grapheneapps.enotes.data.backup

import com.grapheneapps.enotes.data.db.dao.FolderDao
import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.entity.toDomain
import com.grapheneapps.enotes.data.sync.EnoteSerializer
import com.grapheneapps.enotes.data.sync.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val noteCount: Int = 0,
    val folderCount: Int = 0,
)

@Singleton
class BackupManager @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val webDavClient: WebDavClient,
) {
    private val json = Json { prettyPrint = false }

    suspend fun backupToWebDav(baseUrl: String, username: String, password: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val backupUrl = baseUrl.trimEnd('/') + "/eNotes-backup"
                webDavClient.mkcol(backupUrl, username, password)

                val notes = noteDao.getAllNonDeleted()
                var uploaded = 0

                for (entity in notes) {
                    val note = entity.toDomain()
                    val enoteJson = EnoteSerializer.toJson(note)
                    val success = webDavClient.put(
                        "$backupUrl/${note.id}.enote",
                        username, password,
                        enoteJson.toByteArray(),
                    )
                    if (success) uploaded++
                }

                // Upload manifest
                val manifest = BackupManifest(noteCount = uploaded, folderCount = 0)
                webDavClient.put(
                    "$backupUrl/manifest.json",
                    username, password,
                    json.encodeToString(manifest).toByteArray(),
                )

                Timber.d("Backup complete: $uploaded notes")
                Result.success(uploaded)
            } catch (e: Exception) {
                Timber.e("Backup failed: ${e.message}")
                Result.failure(e)
            }
        }
}
