package dev.ecalendar.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // SYNOLOGY, ZOHO, ICAL_SUBSCRIPTION
    val displayName: String,
    val baseUrl: String,
    val username: String = "",
    val colorHex: String = "#4285F4",
    val lastSyncedAt: Long? = null,
    val isEnabled: Boolean = true,
)
