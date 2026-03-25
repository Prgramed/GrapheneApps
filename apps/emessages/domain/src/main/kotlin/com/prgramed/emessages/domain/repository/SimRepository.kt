package com.prgramed.emessages.domain.repository

import com.prgramed.emessages.domain.model.SimInfo
import kotlinx.coroutines.flow.Flow

interface SimRepository {
    fun getActiveSimsFlow(): Flow<List<SimInfo>>
    fun isDualSim(): Boolean
    fun getDefaultSmsSubscriptionId(): Int
    fun getPreferredSim(threadId: Long): Flow<Int?>
    suspend fun setPreferredSim(threadId: Long, subscriptionId: Int)
}
