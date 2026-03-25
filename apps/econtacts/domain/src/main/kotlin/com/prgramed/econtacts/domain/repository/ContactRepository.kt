package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAll(): Flow<List<Contact>>
    suspend fun getById(id: Long): Contact?
    fun search(query: String): Flow<List<Contact>>
    suspend fun insert(contact: Contact): Long
    suspend fun update(contact: Contact)
    suspend fun delete(ids: List<Long>)
    fun getStarred(): Flow<List<Contact>>
}
