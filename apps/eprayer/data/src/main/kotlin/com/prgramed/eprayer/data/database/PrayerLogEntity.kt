package com.prgramed.eprayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_log")
data class PrayerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prayer: String,
    val dateEpochDay: Long,
    val scheduledTimeMillis: Long,
    val prayedTimeMillis: Long? = null,
    val notified: Boolean = false,
)
