package com.prgramed.eprayer.domain.repository

import com.prgramed.eprayer.domain.model.CalculationMethodType
import com.prgramed.eprayer.domain.model.LocationMode
import com.prgramed.eprayer.domain.model.MadhabType
import com.prgramed.eprayer.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    fun getUserPreferences(): Flow<UserPreferences>
    suspend fun updateCalculationMethod(method: CalculationMethodType)
    suspend fun updateLocationMode(mode: LocationMode)
    suspend fun updateManualLocation(latitude: Double, longitude: Double, cityName: String?)
    suspend fun updateMadhab(madhab: MadhabType)
    suspend fun updateNotificationsEnabled(enabled: Boolean)
}
