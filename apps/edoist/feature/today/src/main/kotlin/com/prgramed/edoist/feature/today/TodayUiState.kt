package com.prgramed.edoist.feature.today

import com.prgramed.edoist.domain.model.TaskGroup

data class TodayUiState(
    val taskGroups: List<TaskGroup> = emptyList(),
    val todayCount: Int = 0,
    val isLoading: Boolean = true,
)
