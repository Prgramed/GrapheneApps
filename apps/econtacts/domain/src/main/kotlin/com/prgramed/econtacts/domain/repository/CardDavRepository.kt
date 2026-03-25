package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.CardDavAccount
import com.prgramed.econtacts.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow

interface CardDavRepository {
    suspend fun sync(): SyncResult
    fun getAccount(): Flow<CardDavAccount?>
    suspend fun saveAccount(account: CardDavAccount, password: String)
    suspend fun removeAccount()
    suspend fun testConnection(serverUrl: String, username: String, password: String): Boolean
    suspend fun discover(serverUrl: String, username: String, password: String): String?
    suspend fun ensurePeriodicSyncIfConfigured()
}
