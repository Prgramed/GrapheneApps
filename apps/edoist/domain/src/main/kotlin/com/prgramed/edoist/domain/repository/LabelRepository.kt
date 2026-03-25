package com.prgramed.edoist.domain.repository

import com.prgramed.edoist.domain.model.Label
import kotlinx.coroutines.flow.Flow

interface LabelRepository {

    fun observeAll(): Flow<List<Label>>

    suspend fun create(name: String, color: Long): String

    suspend fun update(label: Label)

    suspend fun delete(labelId: String)
}
