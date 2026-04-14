package com.grapheneapps.enotes.data.sync

import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.entity.toDomain
import com.grapheneapps.enotes.data.db.entity.toEntity
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0,
)

@Singleton
class SyncEngine @Inject constructor(
    private val webDavClient: WebDavClient,
    private val noteDao: NoteDao,
) {
    suspend fun sync(baseUrl: String, username: String, password: String): Result<SyncResult> =
        withContext(Dispatchers.IO) {
            try {
                val syncUrl = baseUrl.trimEnd('/') + "/eNotes"

                // Ensure remote folder exists
                webDavClient.mkcol(syncUrl, username, password)

                // Get remote entries
                val remoteEntries = webDavClient.propfind(syncUrl, username, password)
                    .filter { it.name.endsWith(".enote") && !it.isCollection }
                val remoteByName = remoteEntries.associateBy { it.name.removeSuffix(".enote") }

                // Get local notes
                val localNotes = noteDao.getAllNonDeleted()
                val localById = localNotes.associateBy { it.id }
                val pendingDeletes = noteDao.getPendingDeletes()

                var uploaded = 0
                var downloaded = 0
                var deleted = 0
                var conflicts = 0
                var errors = 0

                // Upload local changes
                for (entity in localNotes) {
                    val note = entity.toDomain()
                    if (note.syncStatus == SyncStatus.PENDING_UPLOAD || note.syncStatus == SyncStatus.LOCAL_ONLY) {
                        val remoteEntry = remoteByName[note.id]
                        if (remoteEntry != null &&
                            note.syncStatus != SyncStatus.LOCAL_ONLY &&
                            remoteEntry.etag != null &&
                            remoteEntry.etag != entity.remoteEtag
                        ) {
                            // Both sides changed — download the remote version and save
                            // it as a new "[Conflict]" note so the user can manually merge.
                            // Local note is preserved unchanged; the local version continues
                            // to be the primary and is uploaded below.
                            val remoteUrl = "$syncUrl/${remoteEntry.name}"
                            val bytes = webDavClient.get(remoteUrl, username, password)
                            val remoteNote = bytes?.let { EnoteSerializer.fromJson(String(it)) }
                            if (remoteNote != null) {
                                val conflictCopy = remoteNote.copy(
                                    id = UUID.randomUUID().toString(),
                                    title = "[Conflict] ${remoteNote.title}",
                                    syncStatus = SyncStatus.CONFLICT,
                                    remoteEtag = null,
                                )
                                noteDao.upsert(conflictCopy.toEntity())
                                conflicts++
                            }
                        }

                        val json = EnoteSerializer.toJson(note)
                        val url = "$syncUrl/${note.id}.enote"
                        val success = webDavClient.put(url, username, password, json.toByteArray())
                        if (success) {
                            noteDao.upsert(entity.copy(syncStatus = SyncStatus.SYNCED.name))
                            uploaded++
                        } else {
                            noteDao.upsert(entity.copy(syncStatus = SyncStatus.ERROR.name))
                            errors++
                        }
                    }
                }

                // Download remote notes not present locally or newer
                for ((noteId, entry) in remoteByName) {
                    val localEntity = localById[noteId]
                    if (localEntity == null) {
                        // New remote note — download
                        val url = "$syncUrl/${entry.name}"
                        val bytes = webDavClient.get(url, username, password)
                        if (bytes != null) {
                            val note = EnoteSerializer.fromJson(String(bytes))
                            if (note != null) {
                                noteDao.upsert(note.toEntity())
                                downloaded++
                            }
                        }
                    } else if (localEntity.syncStatus == SyncStatus.SYNCED.name) {
                        // Check if remote is newer (etag changed)
                        val remoteEtag = entry.etag
                        if (remoteEtag != null && remoteEtag != localEntity.remoteEtag) {
                            val url = "$syncUrl/${entry.name}"
                            val bytes = webDavClient.get(url, username, password)
                            if (bytes != null) {
                                val note = EnoteSerializer.fromJson(String(bytes))
                                if (note != null) {
                                    noteDao.upsert(note.toEntity().copy(
                                        remoteEtag = remoteEtag,
                                        syncStatus = SyncStatus.SYNCED.name,
                                    ))
                                    downloaded++
                                }
                            }
                        }
                    }
                }

                // Delete remote notes that were locally deleted
                for (entity in pendingDeletes) {
                    val url = "$syncUrl/${entity.id}.enote"
                    webDavClient.delete(url, username, password)
                    noteDao.delete(entity.id)
                    deleted++
                }

                val result = SyncResult(uploaded, downloaded, deleted, conflicts, errors)
                Timber.d("Sync complete: $result")
                Result.success(result)
            } catch (e: Exception) {
                Timber.e("Sync failed: ${e.message}")
                Result.failure(e)
            }
        }
}
