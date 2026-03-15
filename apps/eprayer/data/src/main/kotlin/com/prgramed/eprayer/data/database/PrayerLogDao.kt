package com.prgramed.eprayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrayerLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PrayerLogEntity)

    @Query("SELECT * FROM prayer_log WHERE dateEpochDay = :epochDay")
    suspend fun getLogsForDate(epochDay: Long): List<PrayerLogEntity>

    @Query("UPDATE prayer_log SET prayedTimeMillis = :timeMillis WHERE id = :id")
    suspend fun markPrayed(id: Long, timeMillis: Long)

    @Query("UPDATE prayer_log SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)
}
