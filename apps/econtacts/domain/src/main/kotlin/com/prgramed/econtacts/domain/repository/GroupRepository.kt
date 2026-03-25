package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.ContactGroup
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAll(): Flow<List<ContactGroup>>
    suspend fun create(title: String): Long
    suspend fun delete(id: Long)
    suspend fun addMember(groupId: Long, contactId: Long)
    suspend fun removeMember(groupId: Long, contactId: Long)
    fun getMembers(groupId: Long): Flow<List<Contact>>
}
