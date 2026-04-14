package com.prgramed.edoist.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.relation.ProjectWithSections
import com.prgramed.edoist.data.database.relation.ProjectWithTaskCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Upsert
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE is_inbox = 1 LIMIT 1")
    suspend fun getInboxProject(): ProjectEntity?

    @Query("SELECT * FROM projects WHERE is_inbox = 1 LIMIT 1")
    fun observeInboxProject(): Flow<ProjectEntity?>

    @Query(
        """
        SELECT * FROM projects
        WHERE is_archived = 0
        ORDER BY is_inbox DESC, sort_order ASC
        """,
    )
    fun observeAllActive(): Flow<List<ProjectEntity>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProjectWithSections(projectId: String): Flow<ProjectWithSections?>

    @Query("SELECT * FROM projects WHERE is_archived = 0 ORDER BY is_inbox DESC, sort_order ASC")
    suspend fun getAllActive(): List<ProjectEntity>

    @Query(
        """
        SELECT p.*, COUNT(t.id) as task_count FROM projects p
        LEFT JOIN tasks t ON t.project_id = p.id AND t.is_completed = 0 AND t.parent_task_id IS NULL
        WHERE p.is_archived = 0
        GROUP BY p.id
        ORDER BY p.is_inbox DESC, p.sort_order ASC
        """,
    )
    fun observeAllActiveWithTaskCount(): Flow<List<ProjectWithTaskCount>>

    @Query("UPDATE projects SET sort_order = :sortOrder, updated_at_millis = :updatedAtMillis WHERE id = :projectId")
    suspend fun updateSortOrder(projectId: String, sortOrder: Int, updatedAtMillis: Long)

    @Query("SELECT * FROM projects WHERE updated_at_millis >= :sinceMillis")
    suspend fun getProjectsModifiedSince(sinceMillis: Long): List<ProjectEntity>
}
