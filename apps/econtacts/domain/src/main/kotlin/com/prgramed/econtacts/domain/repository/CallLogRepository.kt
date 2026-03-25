package com.prgramed.econtacts.domain.repository

import com.prgramed.econtacts.domain.model.RecentCall
import kotlinx.coroutines.flow.Flow

interface CallLogRepository {
    fun getRecentCalls(limit: Int = 100): Flow<List<RecentCall>>
}
