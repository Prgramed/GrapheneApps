package com.prgramed.eprayer.domain.usecase

import com.prgramed.eprayer.domain.model.QiblaDirection
import com.prgramed.eprayer.domain.repository.QiblaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetQiblaDirectionUseCase @Inject constructor(
    private val qiblaRepository: QiblaRepository,
) {
    operator fun invoke(): Flow<QiblaDirection> =
        qiblaRepository.getQiblaDirection()
}
