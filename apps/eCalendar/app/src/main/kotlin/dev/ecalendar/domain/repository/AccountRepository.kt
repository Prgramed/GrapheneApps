package dev.ecalendar.domain.repository

import dev.ecalendar.caldav.DiscoveryResult
import dev.ecalendar.domain.model.CalendarAccount
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<CalendarAccount>>
    suspend fun addAccount(account: CalendarAccount, password: String): DiscoveryResult
    suspend fun deleteAccount(id: Long)
    suspend fun getById(id: Long): CalendarAccount?
}
