package dev.egallery.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

sealed interface SyncState {
    data class Idle(val lastSyncAt: Long = 0) : SyncState
    data object Syncing : SyncState
    data class Error(val message: String) : SyncState
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncEngine: NasSyncEngine,
    preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    val syncStatus: StateFlow<String> = syncEngine.progress
    val isRunning: StateFlow<Boolean> = syncEngine.isRunning
}
