package dev.ecalendar.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_sources ORDER BY displayName")
    fun observeAll(): Flow<List<CalendarSourceEntity>>

    @Query("SELECT * FROM calendar_sources WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: Long): List<CalendarSourceEntity>

    @Query("SELECT * FROM calendar_sources WHERE id = :id")
    suspend fun getById(id: Long): CalendarSourceEntity?

    @Upsert
    suspend fun upsert(source: CalendarSourceEntity): Long

    @Query("DELETE FROM calendar_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM calendar_sources WHERE calDavUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): CalendarSourceEntity?

    /**
     * First writable + non-mirror remote calendar. Used as a fallback default
     * when the user's `defaultCalendarSourceId` preference is 0 but they have
     * already added a CalDAV account.
     */
    @Query("SELECT * FROM calendar_sources WHERE isReadOnly = 0 AND isMirror = 0 AND accountId != 0 ORDER BY displayName LIMIT 1")
    suspend fun getFirstWritable(): CalendarSourceEntity?

    @Query("UPDATE calendar_sources SET isVisible = :visible WHERE id = :id")
    suspend fun updateVisibility(id: Long, visible: Boolean)
}
