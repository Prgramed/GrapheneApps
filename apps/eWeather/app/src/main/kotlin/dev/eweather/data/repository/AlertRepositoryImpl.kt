package dev.eweather.data.repository

import dev.eweather.data.api.MeteoAlarmCountries
import dev.eweather.data.api.MeteoAlarmParser
import dev.eweather.data.api.MeteoAlarmService
import dev.eweather.data.db.dao.AlertDao
import dev.eweather.data.db.entity.toDomain
import dev.eweather.data.db.entity.toEntity
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherAlert
import dev.eweather.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val alertDao: AlertDao,
    private val meteoAlarmService: MeteoAlarmService,
) : AlertRepository {

    override suspend fun refreshAlerts(location: SavedLocation): List<WeatherAlert> {
        // Only fetch for European locations
        if (!MeteoAlarmCountries.isInEurope(location.lat, location.lon)) {
            return emptyList()
        }

        val slug = MeteoAlarmCountries.getSlug(location.country) ?: return emptyList()

        return try {
            val xml = meteoAlarmService.getFeed(slug)
            val alerts = MeteoAlarmParser.parse(xml)

            // Persist to Room
            alertDao.upsertAlerts(alerts.map { it.toEntity(location.id) })
            alertDao.deleteExpiredAlerts(System.currentTimeMillis())

            alerts
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch MeteoAlarm for ${location.country}")
            emptyList()
        }
    }

    override fun observeActiveAlerts(locationId: Long): Flow<List<WeatherAlert>> =
        alertDao.observeActiveAlerts(locationId, System.currentTimeMillis())
            .map { list -> list.map { it.toDomain() } }
}
