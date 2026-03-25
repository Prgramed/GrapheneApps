package com.prgramed.econtacts.data.sync

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SyncStateEntity::class], version = 1)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncStateDao(): SyncStateDao
}
