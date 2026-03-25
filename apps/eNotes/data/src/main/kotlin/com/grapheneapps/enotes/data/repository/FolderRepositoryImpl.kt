package com.grapheneapps.enotes.data.repository

import com.grapheneapps.enotes.data.db.dao.FolderDao
import com.grapheneapps.enotes.data.db.entity.toDomain
import com.grapheneapps.enotes.data.db.entity.toEntity
import com.grapheneapps.enotes.domain.model.Folder
import com.grapheneapps.enotes.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
) : FolderRepository {

    override fun observeAll(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRootFolders(): Flow<List<Folder>> =
        folderDao.observeRootFolders().map { list -> list.map { it.toDomain() } }

    override fun observeChildren(parentId: String): Flow<List<Folder>> =
        folderDao.observeChildren(parentId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Folder? =
        folderDao.getById(id)?.toDomain()

    override suspend fun save(folder: Folder) {
        folderDao.upsert(folder.toEntity())
    }

    override suspend fun delete(id: String) {
        folderDao.delete(id)
    }
}
