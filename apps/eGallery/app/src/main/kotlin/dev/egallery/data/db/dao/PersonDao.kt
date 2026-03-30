package dev.egallery.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.egallery.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<PersonEntity>>

    @Upsert
    suspend fun upsertAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}
