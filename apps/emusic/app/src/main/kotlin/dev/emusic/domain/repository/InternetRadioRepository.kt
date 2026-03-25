package dev.emusic.domain.repository

import dev.emusic.data.db.entity.CountryEntity
import dev.emusic.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface InternetRadioRepository {

    suspend fun getTopVoted(count: Int = 20): List<RadioStation>
    suspend fun getTopClicked(count: Int = 20): List<RadioStation>
    suspend fun getStationsByCountry(code: String, offset: Int = 0, limit: Int = 40): List<RadioStation>
    suspend fun searchStations(name: String, offset: Int = 0, limit: Int = 40): List<RadioStation>
    suspend fun getStationsByTag(tag: String, offset: Int = 0, limit: Int = 40): List<RadioStation>
    suspend fun reportClick(uuid: String)

    suspend fun syncCountries()
    fun observeCountries(): Flow<List<CountryEntity>>

    fun observeFavourites(): Flow<List<RadioStation>>
    suspend fun isFavourited(uuid: String): Boolean
    suspend fun addFavourite(station: RadioStation)
    suspend fun removeFavourite(uuid: String)
    suspend fun updateLastPlayed(uuid: String)
}
