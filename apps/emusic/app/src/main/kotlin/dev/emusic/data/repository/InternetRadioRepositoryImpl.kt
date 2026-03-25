package dev.emusic.data.repository

import dev.emusic.data.db.dao.CountryDao
import dev.emusic.data.db.dao.RadioStationDao
import dev.emusic.data.db.entity.CountryEntity
import dev.emusic.data.radio.RadioBrowserApiService
import dev.emusic.data.radio.toDomain
import dev.emusic.data.radio.toEntity
import dev.emusic.domain.model.RadioStation
import dev.emusic.domain.repository.InternetRadioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternetRadioRepositoryImpl @Inject constructor(
    private val api: RadioBrowserApiService,
    private val radioStationDao: RadioStationDao,
    private val countryDao: CountryDao,
) : InternetRadioRepository {

    // Cache top stations for 1 hour
    private var cachedTopVoted: List<RadioStation>? = null
    private var cachedTopClicked: List<RadioStation>? = null
    private var topVotedFetchedAt = 0L
    private var topClickedFetchedAt = 0L
    private val radioCacheTtl = 60L * 60 * 1000 // 1 hour

    override suspend fun getTopVoted(count: Int): List<RadioStation> {
        val cached = cachedTopVoted
        if (cached != null && System.currentTimeMillis() - topVotedFetchedAt < radioCacheTtl) return cached
        return try {
            val result = enrichFavourites(api.getTopVoted(count).map { it.toDomain() })
            cachedTopVoted = result
            topVotedFetchedAt = System.currentTimeMillis()
            result
        } catch (_: Exception) { cached ?: emptyList() }
    }

    override suspend fun getTopClicked(count: Int): List<RadioStation> {
        val cached = cachedTopClicked
        if (cached != null && System.currentTimeMillis() - topClickedFetchedAt < radioCacheTtl) return cached
        return try {
            val result = enrichFavourites(api.getTopClicked(count).map { it.toDomain() })
            cachedTopClicked = result
            topClickedFetchedAt = System.currentTimeMillis()
            result
        } catch (_: Exception) { cached ?: emptyList() }
    }

    override suspend fun getStationsByCountry(code: String, offset: Int, limit: Int): List<RadioStation> =
        try { enrichFavourites(api.getStationsByCountry(code, offset, limit).map { it.toDomain() }) } catch (_: Exception) { emptyList() }

    override suspend fun searchStations(name: String, offset: Int, limit: Int): List<RadioStation> =
        try { enrichFavourites(api.searchStations(name, offset, limit).map { it.toDomain() }) } catch (_: Exception) { emptyList() }

    override suspend fun getStationsByTag(tag: String, offset: Int, limit: Int): List<RadioStation> =
        try { enrichFavourites(api.getStationsByTag(tag, offset, limit).map { it.toDomain() }) } catch (_: Exception) { emptyList() }

    private suspend fun enrichFavourites(stations: List<RadioStation>): List<RadioStation> {
        if (stations.isEmpty()) return stations
        val favUuids = radioStationDao.getFavouritedUuids(stations.map { it.stationUuid }).toSet()
        return stations.map { it.copy(isFavourite = it.stationUuid in favUuids) }
    }

    override suspend fun reportClick(uuid: String) {
        try { api.reportStationClick(uuid) } catch (_: Exception) { }
    }

    override suspend fun syncCountries() {
        try {
            val countries = api.getCountries().map { it.toEntity() }
            countryDao.upsertAll(countries)
        } catch (_: Exception) { }
    }

    override fun observeCountries(): Flow<List<CountryEntity>> =
        countryDao.observeAll()

    override fun observeFavourites(): Flow<List<RadioStation>> =
        radioStationDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun isFavourited(uuid: String): Boolean =
        radioStationDao.getByUuid(uuid) != null

    override suspend fun addFavourite(station: RadioStation) {
        radioStationDao.upsert(station.toEntity())
    }

    override suspend fun removeFavourite(uuid: String) {
        radioStationDao.deleteByUuid(uuid)
    }

    override suspend fun updateLastPlayed(uuid: String) {
        radioStationDao.updateLastPlayed(uuid, System.currentTimeMillis())
    }
}
