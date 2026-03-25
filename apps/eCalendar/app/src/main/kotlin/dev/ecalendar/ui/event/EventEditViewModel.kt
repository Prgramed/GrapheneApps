package dev.ecalendar.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.alarm.EventAlarmScheduler
import dev.ecalendar.data.preferences.AppPreferencesRepository
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.model.EditScope
import dev.ecalendar.domain.model.EditableEvent
import dev.ecalendar.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val alarmScheduler: EventAlarmScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editUid: String? = savedStateHandle.get<String>("uid")
    private val prefillStartMillis: Long = savedStateHandle.get<Long>("startMillis")
        ?: System.currentTimeMillis()

    val isNew: Boolean = editUid == null

    private val _event = MutableStateFlow(EditableEvent())
    val event: StateFlow<EditableEvent> = _event.asStateFlow()

    private var initialEvent = EditableEvent()

    val isDirty: StateFlow<Boolean> = _event.map { it != initialEvent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val calendars: StateFlow<List<CalendarSource>> = calendarRepository.observeCalendars()
        .map { sources -> sources.filter { !it.isReadOnly && !it.isMirror } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.preferencesFlow.first()

            if (editUid != null) {
                // Editing existing event
                val series = calendarRepository.getEventSeries(editUid)
                if (series != null) {
                    val edited = EditableEvent(
                        uid = series.uid,
                        calendarSourceId = series.calendarSourceId,
                        originalIcs = series.rawIcs,
                    )
                    // TODO: parse ICS fields into EditableEvent when ICalParser supports it
                    _event.value = edited
                    initialEvent = edited
                }
            } else {
                // New event — pre-fill defaults
                val startMs = roundToNextHour(prefillStartMillis)
                val newEvent = EditableEvent(
                    startMillis = startMs,
                    endMillis = startMs + 3_600_000,
                    calendarSourceId = prefs.defaultCalendarSourceId,
                    alarms = listOf(prefs.defaultReminderMins),
                )
                _event.value = newEvent
                initialEvent = newEvent
            }
        }
    }

    fun updateTitle(title: String) {
        _event.value = _event.value.copy(title = title)
    }

    fun updateStartMillis(millis: Long) {
        val current = _event.value
        val newEnd = if (current.endMillis <= millis) millis + 3_600_000 else current.endMillis
        _event.value = current.copy(startMillis = millis, endMillis = newEnd)
    }

    fun updateEndMillis(millis: Long) {
        _event.value = _event.value.copy(endMillis = millis)
    }

    fun toggleAllDay() {
        _event.value = _event.value.copy(isAllDay = !_event.value.isAllDay)
    }

    fun updateLocation(location: String) {
        _event.value = _event.value.copy(location = location.ifBlank { null })
    }

    fun updateNotes(notes: String) {
        _event.value = _event.value.copy(notes = notes.ifBlank { null })
    }

    fun updateUrl(url: String) {
        _event.value = _event.value.copy(url = url.ifBlank { null })
    }

    fun updateColor(hex: String?) {
        _event.value = _event.value.copy(colorHex = hex)
    }

    fun updateCalendarSource(id: Long) {
        _event.value = _event.value.copy(calendarSourceId = id)
    }

    fun updateTravelTime(mins: Int?) {
        _event.value = _event.value.copy(travelTimeMins = mins)
    }

    fun updateRRule(rrule: String?) {
        _event.value = _event.value.copy(rruleString = rrule)
    }

    fun addAttendee(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) return
        val current = _event.value
        if (trimmed !in current.attendees) {
            _event.value = current.copy(attendees = current.attendees + trimmed)
        }
    }

    fun removeAttendee(email: String) {
        val current = _event.value
        _event.value = current.copy(attendees = current.attendees - email)
    }

    fun addAlarm(mins: Int) {
        val current = _event.value
        if (mins !in current.alarms) {
            _event.value = current.copy(alarms = (current.alarms + mins).sorted())
        }
    }

    fun removeAlarm(mins: Int) {
        val current = _event.value
        _event.value = current.copy(alarms = current.alarms - mins)
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _saveResult.value = SaveResult.Saving
            try {
                val editable = _event.value
                if (editable.title.isBlank()) {
                    _saveResult.value = SaveResult.Error("Title is required")
                    return@launch
                }
                if (isNew) {
                    calendarRepository.createEvent(editable)
                } else {
                    calendarRepository.updateEvent(editUid!!, editable, EditScope.ALL)
                }
                // Schedule alarms
                if (editable.alarms.isNotEmpty()) {
                    alarmScheduler.scheduleForEvent(
                        uid = editable.uid ?: editUid ?: "",
                        instanceStart = editable.startMillis,
                        alarmMins = editable.alarms,
                        title = editable.title,
                        location = editable.location,
                    )
                }
                _saveResult.value = SaveResult.Success
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.message ?: "Save failed")
            }
        }
    }

    private fun roundToNextHour(millis: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.HOUR_OF_DAY, 1)
        }
        return cal.timeInMillis
    }
}

sealed class SaveResult {
    data object Idle : SaveResult()
    data object Saving : SaveResult()
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}
