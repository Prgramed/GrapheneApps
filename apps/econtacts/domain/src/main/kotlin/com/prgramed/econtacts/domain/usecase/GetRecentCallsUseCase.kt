package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.model.RecentCall
import com.prgramed.econtacts.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentCallsUseCase @Inject constructor(
    private val callLogRepository: CallLogRepository,
) {
    operator fun invoke(limit: Int = 100): Flow<List<RecentCall>> =
        callLogRepository.getRecentCalls(limit)
}
