package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.prgramed.edoist.data.database.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Upsert
    suspend fun insert(label: LabelEntity)

    @Update
    suspend fun update(label: LabelEntity)

    @Delete
    suspend fun delete(label: LabelEntity)

    @Query("SELECT * FROM labels ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY sort_order ASC")
    suspend fun getAll(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE id = :labelId")
    suspend fun getById(labelId: String): LabelEntity?
}
