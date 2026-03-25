package com.grapheneapps.enotes.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.grapheneapps.enotes.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, editedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY isPinned DESC, editedAt DESC")
    fun observeByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE notes SET deletedAt = :timestamp, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.deletedAt IS NULL
        ORDER BY notes.editedAt DESC
    """)
    fun searchFts(query: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :beforeTimestamp")
    suspend fun purgeDeletedBefore(beforeTimestamp: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    suspend fun getAllNonDeleted(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeletes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isLocked = 1 AND deletedAt IS NULL ORDER BY editedAt DESC")
    fun observeLocked(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE syncStatus = 'CONFLICT' AND deletedAt IS NULL ORDER BY editedAt DESC")
    fun observeConflicts(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAllByCreatedAt(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE ASC")
    fun observeAllByTitle(): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT tags FROM notes WHERE tags != '' AND deletedAt IS NULL")
    fun observeAllTags(): Flow<List<String>>
}
