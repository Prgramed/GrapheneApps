package com.prgramed.edoist.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.prgramed.edoist.data.importer.ImportResult
import com.prgramed.edoist.data.importer.TodoistCsvImporter
import com.prgramed.edoist.data.sync.SyncManager
import com.prgramed.edoist.data.sync.SyncResult
import com.prgramed.edoist.data.sync.SyncWorker
import com.prgramed.edoist.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val todoistImporter: TodoistCsvImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult = _importResult.asStateFlow()

    val uiState = combine(
        userPreferencesRepository.getPreferences(),
        isSyncing,
    ) { prefs, syncing ->
        SettingsUiState(
            syncEnabled = prefs.syncEnabled,
            webDavUrl = prefs.webDavUrl,
            webDavUsername = prefs.username,
            syncIntervalMinutes = prefs.syncIntervalMinutes,
            showCompletedTasks = prefs.showCompletedTasks,
            dynamicColor = prefs.dynamicColor,
            themeMode = prefs.themeMode,
            isSyncing = syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onSyncToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateSyncEnabled(enabled)
            if (enabled) {
                SyncWorker.enqueuePeriodicSync(
                    context,
                    uiState.value.syncIntervalMinutes,
                )
            } else {
                WorkManager.getInstance(context)
                    .cancelUniqueWork(SyncWorker.PERIODIC_WORK_NAME)
            }
        }
    }

    fun onShowCompletedToggled(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateShowCompletedTasks(show)
        }
    }

    fun onDynamicColorToggled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDynamicColor(enabled)
        }
    }

    fun onThemeModeChanged(mode: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeMode(mode)
        }
    }

    fun onSyncIntervalChanged(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateSyncInterval(minutes)
            if (uiState.value.syncEnabled) {
                SyncWorker.enqueuePeriodicSync(context, minutes)
            }
        }
    }

    fun saveWebDavConfig(url: String, username: String, password: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateWebDavConfig(url, username, password)
        }
    }

    fun syncNow() {
        isSyncing.value = true
        viewModelScope.launch {
            val result = syncManager.sync()
            android.util.Log.d("eDoist", "Sync result: $result")
            isSyncing.value = false
        }
    }

    fun importFromTodoist(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val result = inputStream.use { todoistImporter.importFromZip(it) }
                _importResult.value = result
                android.util.Log.d("eDoist", "Import: ${result.projectsImported} projects, ${result.tasksImported} tasks, ${result.sectionsImported} sections")
            } catch (e: Exception) {
                _importResult.value = ImportResult(0, 0, 0, listOf(e.message ?: "Unknown error"))
            }
        }
    }

    fun dismissImportResult() {
        _importResult.value = null
    }

    fun clearSyncConfig() {
        viewModelScope.launch {
            userPreferencesRepository.updateWebDavConfig("", "", "")
            userPreferencesRepository.updateSyncEnabled(false)
            WorkManager.getInstance(context)
                .cancelUniqueWork(SyncWorker.PERIODIC_WORK_NAME)
        }
    }

    companion object {
        private val SYNC_INTERVALS = listOf(15, 30, 60, 360, 1440)

        fun nextSyncInterval(current: Int): Int {
            val currentIndex = SYNC_INTERVALS.indexOf(current)
            return if (currentIndex == -1 || currentIndex == SYNC_INTERVALS.lastIndex) {
                SYNC_INTERVALS.first()
            } else {
                SYNC_INTERVALS[currentIndex + 1]
            }
        }

        fun formatSyncInterval(minutes: Int): String = when (minutes) {
            15 -> "15 minutes"
            30 -> "30 minutes"
            60 -> "1 hour"
            360 -> "6 hours"
            1440 -> "24 hours"
            else -> "$minutes minutes"
        }
    }
}
