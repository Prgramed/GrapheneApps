package com.prgramed.edoist.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: String = "singleton",
    @ColumnInfo(name = "last_sync_millis") val lastSyncMillis: Long? = null,
    @ColumnInfo(name = "last_sync_status") val lastSyncStatus: String? = null,
    @ColumnInfo(name = "pending_changes_count") val pendingChangesCount: Int = 0,
    @ColumnInfo(name = "server_etag") val serverEtag: String? = null,
)
