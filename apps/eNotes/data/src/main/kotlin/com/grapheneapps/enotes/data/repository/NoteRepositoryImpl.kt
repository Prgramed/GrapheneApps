package com.grapheneapps.enotes.data.repository

import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.dao.NoteRevisionDao
import com.grapheneapps.enotes.data.db.entity.NoteRevisionEntity
import com.grapheneapps.enotes.data.db.entity.toDomain
import com.grapheneapps.enotes.data.db.entity.toEntity
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val noteRevisionDao: NoteRevisionDao,
) : NoteRepository {

    override fun observeAll(): Flow<List<Note>> =
        noteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByFolder(folderId: String): Flow<List<Note>> =
        noteDao.observeByFolder(folderId).map { list -> list.map { it.toDomain() } }

    override fun observeDeleted(): Flow<List<Note>> =
        noteDao.observeDeleted().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Note? =
        noteDao.getById(id)?.toDomain()

    override suspend fun save(note: Note) {
        // Create revision snapshot before saving (only when body actually changed)
        val existing = noteDao.getById(note.id)
        if (existing != null && existing.bodyJson.isNotBlank() && existing.bodyJson != note.bodyJson) {
            val deltaChars = note.bodyJson.length - existing.bodyJson.length
            noteRevisionDao.insert(
                NoteRevisionEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = note.id,
                    bodySnapshot = existing.bodyJson,
                    encryptedSnapshot = existing.encryptedBody,
                    createdAt = System.currentTimeMillis(),
                    deltaChars = deltaChars,
                ),
            )
            noteRevisionDao.deleteOldest(note.id, 20)
        }
        noteDao.upsert(note.toEntity())
    }

    override suspend fun softDelete(id: String) {
        noteDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: String) {
        noteDao.restore(id)
    }

    override suspend fun permanentlyDelete(id: String) {
        noteDao.delete(id)
    }

    override fun search(query: String): Flow<List<Note>> {
        val ftsQuery = query.replace("\"", "").replace("*", "").trim() + "*"
        return noteDao.searchFts(ftsQuery).map { list -> list.map { it.toDomain() } }
    }

    override fun observeLocked(): Flow<List<Note>> =
        noteDao.observeLocked().map { list -> list.map { it.toDomain() } }

    override fun observeConflicts(): Flow<List<Note>> =
        noteDao.observeConflicts().map { list -> list.map { it.toDomain() } }

    override fun observeAllByCreatedAt(): Flow<List<Note>> =
        noteDao.observeAllByCreatedAt().map { list -> list.map { it.toDomain() } }

    override fun observeAllByTitle(): Flow<List<Note>> =
        noteDao.observeAllByTitle().map { list -> list.map { it.toDomain() } }

    override fun observeAllTags(): Flow<List<String>> =
        noteDao.observeAllTags().map { tagStrings ->
            tagStrings.flatMap { it.split(",") }.filter { it.isNotBlank() }.distinct().sorted()
        }
}
