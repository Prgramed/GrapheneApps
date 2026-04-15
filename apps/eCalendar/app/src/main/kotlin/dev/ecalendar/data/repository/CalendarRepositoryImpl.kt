package dev.ecalendar.data.repository

import dev.ecalendar.alarm.EventAlarmScheduler
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
import dev.ecalendar.sync.OfflineQueueProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val calendarDao: CalendarDao,
    private val syncQueueDao: SyncQueueDao,
    private val alarmScheduler: EventAlarmScheduler,
    private val offlineQueueProcessor: OfflineQueueProcessor,
) : CalendarRepository {

    // Fire-and-forget drain after create/update/delete so edits push to the
    // server immediately instead of waiting for the next periodic SyncWorker
    // tick (every 6h) or a manual "Sync now" tap.
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun kickDrain() {
        pushScope.launch {
            try { offlineQueueProcessor.drainQueue() } catch (e: Exception) {
                Timber.w(e, "Immediate queue drain failed")
            }
        }
    }

    override fun observeEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>> =
        eventDao.getEventsInRange(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeCalendars(): Flow<List<CalendarSource>> =
        calendarDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEventSeries(uid: String): EventSeries? =
        eventDao.getSeriesByUid(uid)?.toDomain()

    override suspend fun getEventInstance(uid: String, instanceStart: Long): CalendarEvent? =
        eventDao.getEventInstance(uid, instanceStart)?.toDomain()

    override suspend fun getCalendarSource(id: Long): CalendarSource? =
        calendarDao.getById(id)?.toDomain()

    override suspend fun createEvent(editable: EditableEvent): Long {
        // Resolve the calendar source. If the caller passed id=0 (local-only
        // default) but a writable remote calendar exists, use that one —
        // otherwise every new event saved before the user visits Settings
        // ends up stuck locally with no way to sync.
        var resolvedSourceId = editable.calendarSourceId
        var source = calendarDao.getById(resolvedSourceId)
        if (source == null) {
            val writable = calendarDao.getFirstWritable()
            if (writable != null) {
                Timber.i("createEvent: no source for id=$resolvedSourceId, falling back to '${writable.displayName}' (id=${writable.id})")
                resolvedSourceId = writable.id
                source = writable
            }
        }

        val effectiveEditable = if (resolvedSourceId != editable.calendarSourceId) {
            editable.copy(calendarSourceId = resolvedSourceId)
        } else editable

        // Generate ICS
        val icsString = ICalGenerator.generateEventIcs(effectiveEditable)

        // Parse into EventSeries
        val series = ICalParser.parseEventSeries(
            icsString = icsString,
            calendarSourceId = effectiveEditable.calendarSourceId,
            etag = "",
            serverUrl = "",
        ).copy(isLocal = true)

        // Expand into instances
        val now = LocalDate.now()
        val events = RecurrenceExpander.expand(series, now.minusYears(1), now.plusYears(1))

        // Write to Room
        eventDao.upsertSeries(series.toEntity())
        events.forEach { eventDao.upsertEvent(it.toEntity()) }

        // Schedule alarms
        if (effectiveEditable.alarms.isNotEmpty()) {
            events.forEach { event ->
                alarmScheduler.scheduleForEvent(
                    uid = event.uid, instanceStart = event.instanceStart,
                    alarmMins = effectiveEditable.alarms, title = event.title, location = event.location,
                )
            }
        }

        // Enqueue for sync (only if calendar source exists)
        if (source == null) {
            Timber.w("createEvent: no remote calendar source available — event stays local")
            return 0L
        }
        val queueId = syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source.accountId,
                calendarUrl = source.calDavUrl,
                eventUid = series.uid,
                operation = SyncOp.CREATE.name,
                icsPayload = icsString,
            ),
        )
        kickDrain()
        return queueId
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

        // Reschedule alarms for updated events
        if (editable.alarms.isNotEmpty()) {
            events.forEach { event ->
                alarmScheduler.scheduleForEvent(
                    uid = event.uid, instanceStart = event.instanceStart,
                    alarmMins = editable.alarms, title = event.title, location = event.location,
                )
            }
        }

        // Enqueue sync (only if calendar source exists)
        val source = calendarDao.getById(existingSeries.calendarSourceId) ?: return
        syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source.accountId,
                calendarUrl = source.calDavUrl,
                eventUid = uid,
                operation = SyncOp.UPDATE.name,
                icsPayload = icsString,
            ),
        )
        kickDrain()
    }

    override suspend fun deleteEvent(uid: String, scope: EditScope) {
        val series = eventDao.getSeriesByUid(uid)?.toDomain() ?: return

        // Delete from Room
        eventDao.deleteEventsByUid(uid)
        eventDao.deleteSeriesByUid(uid)

        // Enqueue sync (only if calendar source exists)
        val source = calendarDao.getById(series.calendarSourceId) ?: return
        syncQueueDao.enqueue(
            SyncQueueEntity(
                accountId = source.accountId,
                calendarUrl = series.serverUrl,
                eventUid = uid,
                operation = SyncOp.DELETE.name,
            ),
        )
        kickDrain()
    }
}
