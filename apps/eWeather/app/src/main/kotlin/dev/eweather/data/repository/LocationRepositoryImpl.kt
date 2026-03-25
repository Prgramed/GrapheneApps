package dev.eweather.data.repository

import dev.eweather.data.db.dao.LocationDao
import dev.eweather.data.db.entity.LocationEntity
import dev.eweather.data.db.entity.toDomain
import dev.eweather.data.db.entity.toEntity
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
) : LocationRepository {

    override fun observeAll(): Flow<List<SavedLocation>> =
        locationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(location: SavedLocation): Long =
        locationDao.insert(location.toEntity())

    override suspend fun update(location: SavedLocation) =
        locationDao.update(location.toEntity())

    override suspend fun delete(location: SavedLocation) =
        locationDao.delete(location.toEntity())

    override suspend fun getById(id: Long): SavedLocation? =
        locationDao.getById(id)?.toDomain()

    override suspend fun getOrCreateGpsLocation(lat: Double, lon: Double): SavedLocation {
        val existing = locationDao.getGpsLocation()
        if (existing != null) {
            val updated = existing.copy(lat = lat, lon = lon)
            locationDao.update(updated)
            return updated.toDomain()
        }
        val entity = LocationEntity(
            name = "Current Location",
            lat = lat,
            lon = lon,
            isGps = true,
            sortOrder = -1, // GPS always first
        )
        val id = locationDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }
}
