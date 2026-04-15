package dev.ecalendar.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ecalendar.data.preferences.AppPreferences
import dev.ecalendar.data.preferences.AppPreferencesRepository
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.domain.repository.CalendarRepository
import dev.ecalendar.sync.SyncCoordinator
import dev.ecalendar.sync.SyncState
import dev.ecalendar.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val calendarRepository: CalendarRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    val writableCalendars: StateFlow<List<CalendarSource>> = calendarRepository.observeCalendars()
        .map { sources -> sources.filter { !it.isReadOnly && !it.isMirror } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncState> = syncCoordinator.syncState

    fun updateTimeFormat(is24h: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTimeFormat(is24h) }
    }

    fun updateFirstDayOfWeek(day: Int) {
        viewModelScope.launch { preferencesRepository.updateFirstDayOfWeek(day) }
    }

    fun updateDefaultCalendar(id: Long) {
        viewModelScope.launch { preferencesRepository.updateDefaultCalendar(id) }
    }

    fun updateDefaultReminder(mins: Int) {
        viewModelScope.launch { preferencesRepository.updateDefaultReminder(mins) }
    }

    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNotifications(enabled) }
    }

    fun updateSyncInterval(minutes: Int) {
        viewModelScope.launch { preferencesRepository.updateSyncInterval(minutes) }
        // 0 cancels, anything >0 reschedules (SyncWorker clamps to 15-min minimum).
        SyncWorker.schedule(context, minutes)
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
    }

    fun syncNow() {
        // Settings "Sync now" kicks off the full sync (including iCal mirror) via
        // the coordinator's application-lifetime scope — so leaving Settings
        // mid-sync no longer cancels it.
        syncCoordinator.launchSync(includeMirror = true)
    }
}
