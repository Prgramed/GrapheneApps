package com.grapheneapps.enotes.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.grapheneapps.enotes.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY name COLLATE NOCASE")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name COLLATE NOCASE")
    fun observeChildren(parentId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}
