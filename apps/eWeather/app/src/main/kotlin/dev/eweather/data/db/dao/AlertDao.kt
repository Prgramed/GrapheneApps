package dev.eweather.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.eweather.data.db.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Upsert
    suspend fun upsertAlerts(alerts: List<AlertEntity>)

    @Query("SELECT * FROM alerts WHERE locationId = :locationId AND expires > :now ORDER BY expires ASC")
    fun observeActiveAlerts(locationId: Long, now: Long): Flow<List<AlertEntity>>

    @Query("DELETE FROM alerts WHERE expires < :now")
    suspend fun deleteExpiredAlerts(now: Long)
}
