package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.emusic.data.db.entity.RadioStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Upsert
    suspend fun upsert(station: RadioStationEntity)

    @Query("SELECT * FROM radio_stations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE stationUuid = :uuid")
    suspend fun getByUuid(uuid: String): RadioStationEntity?

    @Query("DELETE FROM radio_stations WHERE stationUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("UPDATE radio_stations SET lastPlayedAt = :timestamp WHERE stationUuid = :uuid")
    suspend fun updateLastPlayed(uuid: String, timestamp: Long)

    @Query("SELECT stationUuid FROM radio_stations WHERE stationUuid IN (:uuids)")
    suspend fun getFavouritedUuids(uuids: List<String>): List<String>
}
