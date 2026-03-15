package com.prgramed.eprayer.domain.repository

import com.prgramed.eprayer.domain.model.QiblaDirection
import kotlinx.coroutines.flow.Flow

interface QiblaRepository {
    fun getQiblaDirection(): Flow<QiblaDirection>
}
