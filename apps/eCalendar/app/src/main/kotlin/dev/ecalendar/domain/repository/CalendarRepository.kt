package dev.ecalendar.domain.repository

import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.model.EditScope
import dev.ecalendar.domain.model.EditableEvent
import dev.ecalendar.domain.model.EventSeries
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun observeEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>>
    fun observeCalendars(): Flow<List<CalendarSource>>
    suspend fun getEventSeries(uid: String): EventSeries?
    suspend fun createEvent(editable: EditableEvent): Long
    suspend fun updateEvent(uid: String, editable: EditableEvent, scope: EditScope)
    suspend fun deleteEvent(uid: String, scope: EditScope)
}
