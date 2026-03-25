package com.prgramed.edoist.feature.inbox

import com.prgramed.edoist.domain.model.Task

data class InboxUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
)
