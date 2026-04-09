package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.prgramed.edoist.data.database.entity.SectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Upsert
    suspend fun insert(section: SectionEntity)

    @Update
    suspend fun update(section: SectionEntity)

    @Delete
    suspend fun delete(section: SectionEntity)

    @Query("SELECT * FROM sections WHERE project_id = :projectId ORDER BY sort_order ASC")
    fun observeByProject(projectId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :sectionId")
    suspend fun getById(sectionId: String): SectionEntity?

    @Query("SELECT * FROM sections WHERE project_id = :projectId ORDER BY sort_order ASC")
    suspend fun getByProjectId(projectId: String): List<SectionEntity>

    @Query("UPDATE sections SET sort_order = :sortOrder WHERE id = :sectionId")
    suspend fun updateSortOrder(sectionId: String, sortOrder: Int)

    @Query("UPDATE sections SET is_collapsed = :isCollapsed WHERE id = :sectionId")
    suspend fun setCollapsed(sectionId: String, isCollapsed: Boolean)
}
