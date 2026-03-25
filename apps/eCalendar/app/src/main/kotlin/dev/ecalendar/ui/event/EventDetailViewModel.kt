package dev.ecalendar.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.alarm.EventAlarmScheduler
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.model.EditScope
import dev.ecalendar.domain.repository.CalendarRepository
import dev.ecalendar.ical.ICalParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val alarmScheduler: EventAlarmScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val uid: String = savedStateHandle.get<String>("uid") ?: ""
    private val instanceStart: Long = savedStateHandle.get<Long>("instanceStart") ?: 0L

    private val _state = MutableStateFlow<EventDetailState>(EventDetailState.Loading)
    val state: StateFlow<EventDetailState> = _state.asStateFlow()

    private val _deleteResult = MutableStateFlow<DeleteResult>(DeleteResult.Idle)
    val deleteResult: StateFlow<DeleteResult> = _deleteResult.asStateFlow()

    init {
        loadEvent()
    }

    private fun loadEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            val event = calendarRepository.getEventInstance(uid, instanceStart)
            if (event == null) {
                _state.value = EventDetailState.Error("Event not found")
                return@launch
            }

            val source = calendarRepository.getCalendarSource(event.calendarSourceId)
            val series = calendarRepository.getEventSeries(uid)
            val rawIcs = series?.rawIcs

            val attendees = if (rawIcs != null) {
                try { ICalParser.parseAttendees(rawIcs) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val alarms = if (rawIcs != null) {
                try { ICalParser.parseAlarms(rawIcs) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val rruleLine = rawIcs?.lineSequence()
                ?.firstOrNull { it.startsWith("RRULE:") }
                ?.removePrefix("RRULE:")

            _state.value = EventDetailState.Loaded(
                event = event,
                source = source,
                attendees = attendees,
                alarmMins = alarms.map { it.offsetMins },
                rruleString = rruleLine,
                isRecurring = rruleLine != null,
            )
        }
    }

    fun deleteEvent(scope: EditScope) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = (_state.value as? EventDetailState.Loaded) ?: return@launch
                // Cancel alarms
                alarmScheduler.cancelForEvent(uid, instanceStart, loaded.alarmMins)
                // Delete from repository
                calendarRepository.deleteEvent(uid, scope)
                _deleteResult.value = DeleteResult.Success
            } catch (e: Exception) {
                _deleteResult.value = DeleteResult.Error(e.message ?: "Delete failed")
            }
        }
    }
}

sealed class EventDetailState {
    data object Loading : EventDetailState()
    data class Loaded(
        val event: CalendarEvent,
        val source: CalendarSource?,
        val attendees: List<String>,
        val alarmMins: List<Int>,
        val rruleString: String?,
        val isRecurring: Boolean,
    ) : EventDetailState()
    data class Error(val message: String) : EventDetailState()
}

sealed class DeleteResult {
    data object Idle : DeleteResult()
    data object Success : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}
