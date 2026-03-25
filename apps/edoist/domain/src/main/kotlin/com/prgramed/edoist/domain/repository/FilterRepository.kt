package com.prgramed.edoist.domain.repository

import com.prgramed.edoist.domain.model.Filter
import kotlinx.coroutines.flow.Flow

interface FilterRepository {

    fun observeAll(): Flow<List<Filter>>

    suspend fun create(filter: Filter): String

    suspend fun update(filter: Filter)

    suspend fun delete(filterId: String)
}
