package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.SpeedDial
import kotlinx.coroutines.flow.Flow

interface SpeedDialRepository {
    fun getAll(): Flow<List<SpeedDial>>
    suspend fun set(key: Int, contactId: Long, number: String, displayName: String)
    suspend fun remove(key: Int)
}
