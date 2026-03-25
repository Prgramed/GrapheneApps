package com.grapheneapps.enotes.domain.repository

import com.grapheneapps.enotes.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeAll(): Flow<List<Folder>>
    fun observeRootFolders(): Flow<List<Folder>>
    fun observeChildren(parentId: String): Flow<List<Folder>>
    suspend fun getById(id: String): Folder?
    suspend fun save(folder: Folder)
    suspend fun delete(id: String)
}
