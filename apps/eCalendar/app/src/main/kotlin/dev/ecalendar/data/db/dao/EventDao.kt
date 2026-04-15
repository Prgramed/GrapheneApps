package dev.ecalendar.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ecalendar.data.db.entity.CalendarEventEntity
import dev.ecalendar.data.db.entity.EventSeriesEntity
import kotlinx.coroutines.flow.Flow

data class EtagEntry(val uid: String, val etag: String)
data class ServerUrlEtag(val serverUrl: String, val etag: String)

@Dao
interface EventDao {

    @Query("SELECT * FROM calendar_events WHERE instanceStart < :end AND instanceEnd > :start AND isCancelled = 0 ORDER BY instanceStart")
    fun getEventsInRange(start: Long, end: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE instanceStart < :dayEnd AND instanceEnd > :dayStart AND isCancelled = 0 ORDER BY instanceStart")
    fun getEventsForDay(dayStart: Long, dayEnd: Long): Flow<List<CalendarEventEntity>>

    @Upsert
    suspend fun upsertSeries(series: EventSeriesEntity)

    @Upsert
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Query("DELETE FROM event_series WHERE uid = :uid")
    suspend fun deleteSeriesByUid(uid: String)

    @Query("DELETE FROM calendar_events WHERE uid = :uid")
    suspend fun deleteEventsByUid(uid: String)

    @Query("SELECT * FROM event_series WHERE uid = :uid")
    suspend fun getSeriesByUid(uid: String): EventSeriesEntity?

    @Query("SELECT uid, etag FROM event_series WHERE calendarSourceId = :calendarSourceId")
    suspend fun getEtags(calendarSourceId: Long): List<EtagEntry>

    @Query("DELETE FROM calendar_events WHERE instanceEnd < :before")
    suspend fun deleteOldInstances(before: Long)

    @Query("SELECT * FROM event_series WHERE serverUrl = :url")
    suspend fun getSeriesByServerUrl(url: String): EventSeriesEntity?

    @Query("SELECT serverUrl, etag FROM event_series WHERE calendarSourceId = :calendarSourceId")
    suspend fun getServerUrlEtags(calendarSourceId: Long): List<ServerUrlEtag>

    @Query("SELECT * FROM calendar_events WHERE uid = :uid AND instanceStart = :instanceStart LIMIT 1")
    suspend fun getEventInstance(uid: String, instanceStart: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE instanceStart > :start AND instanceStart < :end AND isCancelled = 0 ORDER BY instanceStart")
    suspend fun getFutureEvents(start: Long, end: Long): List<CalendarEventEntity>

    @Query("SELECT COUNT(*) FROM event_series WHERE calendarSourceId = :calendarSourceId")
    suspend fun countSeriesForSource(calendarSourceId: Long): Int
}
