package com.prgramed.edoist.domain.repository

import com.prgramed.edoist.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

data class EDoistPreferences(
    val webDavUrl: String = "",
    val username: String = "",
    val passwordEncrypted: String = "",
    val syncEnabled: Boolean = false,
    val syncIntervalMinutes: Int = 30,
    val defaultSortOrder: SortOrder = SortOrder.MANUAL,
    val showCompletedTasks: Boolean = false,
    val dynamicColor: Boolean = true,
)

interface UserPreferencesRepository {

    fun getPreferences(): Flow<EDoistPreferences>

    suspend fun updateWebDavConfig(url: String, username: String, password: String)

    suspend fun updateSyncEnabled(enabled: Boolean)

    suspend fun updateSyncInterval(minutes: Int)

    suspend fun updateDefaultSortOrder(sortOrder: SortOrder)

    suspend fun updateShowCompletedTasks(show: Boolean)

    suspend fun updateDynamicColor(enabled: Boolean)
}
