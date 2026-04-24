package dev.ecalendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ecalendar.data.preferences.AppPreferences
import dev.ecalendar.data.preferences.AppPreferencesRepository
import dev.ecalendar.data.preferences.CalendarView
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.repository.CalendarRepository
import dev.ecalendar.sync.NetworkMonitor
import dev.ecalendar.sync.SyncCoordinator
import dev.ecalendar.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val syncCoordinator: SyncCoordinator,
    private val calendarDao: dev.ecalendar.data.db.dao.CalendarDao,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _activeDate = MutableStateFlow(LocalDate.now())
    val activeDate: StateFlow<LocalDate> = _activeDate.asStateFlow()

    private val _activeView = MutableStateFlow(CalendarView.MONTH)
    val activeView: StateFlow<CalendarView> = _activeView.asStateFlow()

    private val _visibleCalendars = MutableStateFlow<Set<Long>>(emptySet())
    val visibleCalendars: StateFlow<Set<Long>> = _visibleCalendars.asStateFlow()

    val syncState: StateFlow<SyncState> = syncCoordinator.syncState

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    val calendars: StateFlow<List<CalendarSource>> = calendarRepository.observeCalendars()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Restore from DataStore
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = preferencesRepository.preferencesFlow.first()
            _activeView.value = prefs.activeView
            if (prefs.activeDateMillis > 0) {
                _activeDate.value = java.time.Instant.ofEpochMilli(prefs.activeDateMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }

        // Track visible calendars
        viewModelScope.launch {
            calendarRepository.observeCalendars().collect { sources ->
                _visibleCalendars.value = sources.filter { it.isVisible }.map { it.id }.toSet()
            }
        }
    }

    fun navigate(date: LocalDate) {
        _activeDate.value = date
        viewModelScope.launch {
            preferencesRepository.updateActiveDate(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
        }
    }

    fun goToToday() {
        navigate(LocalDate.now())
    }

    fun setView(view: CalendarView) {
        _activeView.value = view
        viewModelScope.launch { preferencesRepository.updateActiveView(view) }
    }

    fun eventsForRange(startMillis: Long, endMillis: Long): Flow<List<CalendarEvent>> =
        calendarRepository.observeEventsInRange(startMillis, endMillis)
            .map { events ->
                val visible = _visibleCalendars.value
                if (visible.isEmpty()) events
                else events.filter { it.calendarSourceId in visible }
            }

    fun syncNow() {
        syncCoordinator.launchSync(includeMirror = false)
    }

    fun toggleCalendarVisibility(calendarId: Long, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            calendarDao.updateVisibility(calendarId, visible)
        }
    }
}
