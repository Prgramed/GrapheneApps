package dev.ecalendar.data.repository

import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.dao.SyncQueueDao
import dev.ecalendar.data.db.entity.SyncQueueEntity
import dev.ecalendar.data.db.entity.toDomain
import dev.ecalendar.data.db.entity.toEntity
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.model.EditScope
import dev.ecalendar.domain.model.EditableEvent
import dev.ecalendar.domain.model.EventSeries
import dev.ecalendar.domain.model.SyncOp
import dev.ecalendar.domain.repository.CalendarRepository
import dev.ecalendar.ical.ICalGenerator
import dev.ecalendar.ical.ICalParser
import dev.ecalendar.ical.RecurrenceExpander
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val calendarDao: CalendarDao,
    private val syncQueueDao: SyncQueueDao,
) : CalendarRepository {

    override fun observeEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>> =
        eventDao.getEventsInRange(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeCalendars(): Flow<List<CalendarSource>> =
        calendarDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEventSeries(uid: String): EventSeries? =
        eventDao.getSeriesByUid(uid)?.toDomain()

    override suspend fun createEvent(editable: EditableEvent): Long {
        // Generate ICS
        val icsString = ICalGenerator.generateEventIcs(editable)

        // Parse into EventSeries
        val series = ICalParser.parseEventSeries(
            icsString = icsString,
            calendarSourceId = editable.calendarSourceId,
            etag = "",
            serverUrl = "",
        ).copy(isLocal = true)

        // Expand into instances
        val now = LocalDate.now()
        val events = RecurrenceExpander.expand(series, now.minusYears(1), now.plusYears(1))

        // Write to Room
        eventDao.upsertSeries(series.toEntity())
        events.forEach { eventDao.upsertEvent(it.toEntity()) }

        // Get calendar URL for sync
        val source = calendarDao.getById(editable.calendarSourceId)

        // Enqueue for sync
        return syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source?.accountId ?: 0,
                calendarUrl = source?.calDavUrl ?: "",
                eventUid = series.uid,
                operation = SyncOp.CREATE.name,
                icsPayload = icsString,
            ),
        )
    }

    override suspend fun updateEvent(uid: String, editable: EditableEvent, scope: EditScope) {
        val existingSeries = eventDao.getSeriesByUid(uid)?.toDomain() ?: return

        val icsString = when (scope) {
            EditScope.ALL -> {
                // Regenerate full ICS
                ICalGenerator.generateEventIcs(editable.copy(originalIcs = existingSeries.rawIcs))
            }
            EditScope.THIS_ONLY -> {
                // Generate RECURRENCE-ID exception ICS
                ICalGenerator.generateEventIcs(editable)
            }
            EditScope.THIS_AND_FOLLOWING -> {
                // For now, treat as ALL (proper RRULE splitting is complex)
                ICalGenerator.generateEventIcs(editable.copy(originalIcs = existingSeries.rawIcs))
            }
        }

        // Re-parse and expand
        val newSeries = ICalParser.parseEventSeries(
            icsString, existingSeries.calendarSourceId,
            existingSeries.etag, existingSeries.serverUrl,
        )
        val now = LocalDate.now()
        val events = RecurrenceExpander.expand(newSeries, now.minusYears(1), now.plusYears(1))

        // Update Room
        eventDao.deleteEventsByUid(uid)
        eventDao.upsertSeries(newSeries.toEntity())
        events.forEach { eventDao.upsertEvent(it.toEntity()) }

        // Enqueue sync
        val source = calendarDao.getById(existingSeries.calendarSourceId)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source?.accountId ?: 0,
                calendarUrl = source?.calDavUrl ?: "",
                eventUid = uid,
                operation = SyncOp.UPDATE.name,
                icsPayload = icsString,
            ),
        )
    }

    override suspend fun deleteEvent(uid: String, scope: EditScope) {
        val series = eventDao.getSeriesByUid(uid)?.toDomain() ?: return

        // Delete from Room
        eventDao.deleteEventsByUid(uid)
        eventDao.deleteSeriesByUid(uid)

        // Enqueue sync
        val source = calendarDao.getById(series.calendarSourceId)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source?.accountId ?: 0,
                calendarUrl = series.serverUrl,
                eventUid = uid,
                operation = SyncOp.DELETE.name,
            ),
        )
    }
}
