package dev.ecalendar.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_sources ORDER BY displayName")
    fun observeAll(): Flow<List<CalendarSourceEntity>>

    @Query("SELECT * FROM calendar_sources WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: Long): List<CalendarSourceEntity>

    @Query("SELECT * FROM calendar_sources WHERE id = :id")
    suspend fun getById(id: Long): CalendarSourceEntity?

    @Upsert
    suspend fun upsert(source: CalendarSourceEntity)

    @Query("DELETE FROM calendar_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM calendar_sources WHERE calDavUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): CalendarSourceEntity?
}
