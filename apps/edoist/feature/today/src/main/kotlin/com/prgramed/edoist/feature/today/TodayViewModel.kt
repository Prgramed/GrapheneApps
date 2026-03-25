package com.prgramed.edoist.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.usecase.CompleteTaskUseCase
import com.prgramed.edoist.domain.usecase.GetTodayTasksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    getTodayTasksUseCase: GetTodayTasksUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState = getTodayTasksUseCase()
        .map { groups ->
            TodayUiState(
                taskGroups = groups,
                todayCount = groups.sumOf { it.tasks.size },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(),
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
