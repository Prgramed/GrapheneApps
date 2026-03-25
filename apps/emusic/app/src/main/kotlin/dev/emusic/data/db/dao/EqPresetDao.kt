package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.emusic.data.db.entity.EqPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    @Upsert
    suspend fun upsert(preset: EqPresetEntity)

    @Query("SELECT * FROM eq_presets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EqPresetEntity>>

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
