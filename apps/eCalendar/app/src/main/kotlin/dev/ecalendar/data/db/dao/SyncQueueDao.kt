package dev.ecalendar.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.ecalendar.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE accountId = :accountId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getOldestForAccount(accountId: Long): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT 1")
    suspend fun getOldest(): SyncQueueEntity?

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)
}
