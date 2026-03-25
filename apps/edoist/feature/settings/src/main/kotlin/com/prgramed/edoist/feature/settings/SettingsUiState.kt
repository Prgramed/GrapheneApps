package com.prgramed.edoist.feature.settings

data class SettingsUiState(
    val syncEnabled: Boolean = false,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val syncIntervalMinutes: Int = 60,
    val lastSyncStatus: String? = null,
    val lastSyncMillis: Long? = null,
    val showCompletedTasks: Boolean = false,
    val dynamicColor: Boolean = true,
    val isSyncing: Boolean = false,
)
