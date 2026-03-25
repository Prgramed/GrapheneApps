package dev.ecalendar.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_sources",
    indices = [Index("accountId")],
)
data class CalendarSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val calDavUrl: String,
    val displayName: String,
    val colorHex: String = "#4285F4",
    val ctag: String? = null,
    val isReadOnly: Boolean = false,
    val isVisible: Boolean = true,
    val isMirror: Boolean = false,
)
