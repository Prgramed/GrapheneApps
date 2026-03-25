package com.prgramed.econtacts.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_state",
    indices = [
        Index("contactId", unique = true),
        Index("uid"),
        Index("remoteHref"),
    ],
)
data class SyncStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long = 0,
    val remoteHref: String = "",
    val etag: String = "",
    val uid: String = "",
    val isDirty: Boolean = false,
    val isDeletedLocally: Boolean = false,
    val lastSynced: Long = 0,
)
