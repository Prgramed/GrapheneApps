package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.prgramed.edoist.data.database.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE id = 'singleton'")
    suspend fun get(): SyncMetadataEntity?

    @Query(
        """
        UPDATE sync_metadata
        SET last_sync_millis = :lastSyncMillis,
            last_sync_status = :lastSyncStatus,
            pending_changes_count = :pendingChangesCount
        WHERE id = 'singleton'
        """,
    )
    suspend fun updateSyncResult(
        lastSyncMillis: Long,
        lastSyncStatus: String,
        pendingChangesCount: Int,
    )
}
