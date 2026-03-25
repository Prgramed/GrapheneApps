package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.DuplicateGroup
import kotlinx.coroutines.flow.Flow

interface DuplicateRepository {
    fun findDuplicates(): Flow<List<DuplicateGroup>>
    suspend fun mergeContacts(primaryId: Long, mergeIds: List<Long>)
}
