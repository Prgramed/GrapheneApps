package dev.eweather.domain.repository

import dev.eweather.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeAll(): Flow<List<SavedLocation>>
    suspend fun insert(location: SavedLocation): Long
    suspend fun update(location: SavedLocation)
    suspend fun delete(location: SavedLocation)
    suspend fun getById(id: Long): SavedLocation?
    suspend fun getOrCreateGpsLocation(lat: Double, lon: Double): SavedLocation
}
