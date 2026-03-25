package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.prgramed.edoist.data.database.entity.FilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: FilterEntity)

    @Update
    suspend fun update(filter: FilterEntity)

    @Delete
    suspend fun delete(filter: FilterEntity)

    @Query("SELECT * FROM filters ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE id = :filterId")
    suspend fun getById(filterId: String): FilterEntity?
}
