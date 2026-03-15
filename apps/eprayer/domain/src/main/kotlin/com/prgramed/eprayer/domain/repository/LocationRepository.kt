package com.prgramed.eprayer.domain.repository

import com.prgramed.eprayer.domain.model.LocationInfo
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getCurrentLocation(): Flow<LocationInfo>
    suspend fun getLastKnownLocation(): LocationInfo?
}
