package com.prgramed.eprayer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PrayerLogEntity::class], version = 1)
abstract class PrayerDatabase : RoomDatabase() {
    abstract fun prayerLogDao(): PrayerLogDao
}
