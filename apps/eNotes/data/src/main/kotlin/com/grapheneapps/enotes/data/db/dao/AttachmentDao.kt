package com.grapheneapps.enotes.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.grapheneapps.enotes.data.db.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    fun observeByNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    suspend fun getByNote(noteId: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: String)
}
