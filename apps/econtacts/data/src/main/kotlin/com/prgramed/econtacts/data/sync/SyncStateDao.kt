package com.prgramed.econtacts.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state")
    suspend fun getAll(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE remoteHref = :href LIMIT 1")
    suspend fun getByHref(href: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE contactId = :contactId LIMIT 1")
    suspend fun getByContactId(contactId: Long): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE isDirty = 1")
    suspend fun getDirty(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state WHERE isDeletedLocally = 1")
    suspend fun getDeletedLocally(): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncStateEntity): Long

    @Update
    suspend fun update(entity: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_state WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: Long)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
