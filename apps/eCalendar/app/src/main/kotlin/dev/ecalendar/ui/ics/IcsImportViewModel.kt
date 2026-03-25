package dev.ecalendar.ui.ics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.model.EditableEvent
import dev.ecalendar.domain.repository.CalendarRepository
import dev.ecalendar.ical.ICalParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import java.io.StringReader
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ParsedIcsEvent(
    val uid: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val location: String?,
    val notes: String?,
    val organizer: String?,
    val attendees: List<String>,
    val alarmMins: List<Int>,
    val rawIcs: String,
    val isDuplicate: Boolean = false,
)

sealed class ImportState {
    data object Idle : ImportState()
    data class Parsed(val event: ParsedIcsEvent) : ImportState()
    data object Saved : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class IcsImportViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    var lastParsedEvent: ParsedIcsEvent? = null
        private set

    val writableCalendars: StateFlow<List<CalendarSource>> = calendarRepository.observeCalendars()
        .map { sources -> sources.filter { !it.isReadOnly && !it.isMirror } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun parseIcs(icsContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val builder = CalendarBuilder()
                val calendar = builder.build(StringReader(icsContent))
                val vevent = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
                    ?: throw IllegalArgumentException("No VEVENT found")

                val uid = vevent.getProperty<net.fortuna.ical4j.model.property.Uid>(Property.UID)?.value
                    ?: "imported-${System.currentTimeMillis()}"
                val title = vevent.summary?.value ?: "Untitled Event"
                val location = vevent.location?.value
                val notes = vevent.description?.value

                // Parse dates
                val dtStart = vevent.startDate
                val dtEnd = vevent.endDate
                val zone = ZoneId.systemDefault()
                val startMillis = dtStart?.date?.time ?: System.currentTimeMillis()
                val endMillis = dtEnd?.date?.time ?: (startMillis + 3_600_000)
                val isAllDay = dtStart?.value?.length == 8 // DATE vs DATE-TIME

                // Organizer
                val organizer = vevent.organizer?.calAddress?.toString()?.removePrefix("mailto:")

                // Attendees
                val attendees = ICalParser.parseAttendees(icsContent)

                // Alarms
                val alarms = ICalParser.parseAlarms(icsContent).map { it.offsetMins }

                // Check duplicate
                val existing = calendarRepository.getEventSeries(uid)
                val isDuplicate = existing != null

                val parsedEvent = ParsedIcsEvent(
                    uid = uid,
                    title = title,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    isAllDay = isAllDay,
                    location = location,
                    notes = notes,
                    organizer = organizer,
                    attendees = attendees,
                    alarmMins = alarms,
                    rawIcs = icsContent,
                    isDuplicate = isDuplicate,
                )
                lastParsedEvent = parsedEvent
                _state.value = ImportState.Parsed(parsedEvent)
            } catch (e: Exception) {
                _state.value = ImportState.Error("Failed to parse ICS: ${e.message}")
            }
        }
    }

    fun saveToCalendar(calendarSourceId: Long) {
        val parsed = (_state.value as? ImportState.Parsed)?.event ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val editable = EditableEvent(
                    uid = parsed.uid,
                    title = parsed.title,
                    startMillis = parsed.startMillis,
                    endMillis = parsed.endMillis,
                    isAllDay = parsed.isAllDay,
                    location = parsed.location,
                    notes = parsed.notes,
                    calendarSourceId = calendarSourceId,
                    attendees = parsed.attendees,
                    alarms = parsed.alarmMins.ifEmpty { listOf(15) },
                    originalIcs = parsed.rawIcs,
                )
                calendarRepository.createEvent(editable)
                _state.value = ImportState.Saved
            } catch (e: Exception) {
                _state.value = ImportState.Error("Failed to save: ${e.message}")
            }
        }
    }
}
