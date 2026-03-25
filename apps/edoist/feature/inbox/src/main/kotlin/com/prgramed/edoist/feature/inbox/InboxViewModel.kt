package com.prgramed.edoist.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.usecase.CompleteTaskUseCase
import com.prgramed.edoist.domain.usecase.GetInboxTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    getInboxTasksUseCase: GetInboxTasksUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState = getInboxTasksUseCase()
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
