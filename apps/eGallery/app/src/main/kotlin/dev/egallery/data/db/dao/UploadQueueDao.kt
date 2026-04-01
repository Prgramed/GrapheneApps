package dev.egallery.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.egallery.data.db.entity.UploadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueueDao {

    @Query("SELECT * FROM upload_queue ORDER BY enqueuedAt ASC")
    fun getAll(): Flow<List<UploadQueueEntity>>

    @Query("SELECT * FROM upload_queue WHERE status = 'PENDING' ORDER BY enqueuedAt ASC")
    suspend fun getPending(): List<UploadQueueEntity>

    @Query("SELECT * FROM upload_queue WHERE status = 'FAILED' ORDER BY enqueuedAt ASC")
    suspend fun getFailed(): List<UploadQueueEntity>

    @Query("SELECT COUNT(*) FROM upload_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM upload_queue WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Insert
    suspend fun insert(item: UploadQueueEntity): Long

    @Query("UPDATE upload_queue SET status = :status, retryCount = :retryCount WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, retryCount: Int)

    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM upload_queue")
    suspend fun deleteAll()

    @Query("DELETE FROM upload_queue WHERE localPath LIKE '%' || :pattern || '%'")
    suspend fun deleteByPathContaining(pattern: String): Int
}
