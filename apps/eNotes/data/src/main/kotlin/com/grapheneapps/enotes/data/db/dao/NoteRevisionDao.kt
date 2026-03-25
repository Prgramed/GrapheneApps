package com.grapheneapps.enotes.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.grapheneapps.enotes.data.db.entity.NoteRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRevisionDao {

    @Insert
    suspend fun insert(revision: NoteRevisionEntity)

    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeByNote(noteId: String): Flow<List<NoteRevisionEntity>>

    @Query("SELECT * FROM note_revisions WHERE id = :id")
    suspend fun getById(id: String): NoteRevisionEntity?

    @Query("""
        DELETE FROM note_revisions WHERE noteId = :noteId AND id NOT IN (
            SELECT id FROM note_revisions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :keepCount
        )
    """)
    suspend fun deleteOldest(noteId: String, keepCount: Int = 20)
}
