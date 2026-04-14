package com.prgramed.edoist.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.repository.UserPreferencesRepository
import com.prgramed.edoist.domain.usecase.CompleteTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val taskRepository: TaskRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState = userPreferencesRepository.getPreferences()
        .map { it.showCompletedTasks }
        .flatMapLatest { showCompleted ->
            taskRepository.getInboxTasks(includeCompleted = showCompleted)
        }
        .map { tasks ->
            InboxUiState(
                tasks = tasks,
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InboxUiState(),
        )

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            completeTaskUseCase(taskId)
        }
    }

    fun uncompleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.uncompleteTask(taskId)
        }
    }
}
