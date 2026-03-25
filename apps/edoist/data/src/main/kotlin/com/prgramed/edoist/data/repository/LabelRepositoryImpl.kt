package com.prgramed.edoist.data.repository

import com.prgramed.edoist.data.database.dao.LabelDao
import com.prgramed.edoist.data.database.entity.LabelEntity
import com.prgramed.edoist.domain.model.Label
import com.prgramed.edoist.domain.repository.LabelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepositoryImpl @Inject constructor(
    private val labelDao: LabelDao,
) : LabelRepository {

    private fun LabelEntity.toDomain(): Label = Label(
        id = id,
        name = name,
        color = color,
        sortOrder = sortOrder,
    )

    override fun observeAll(): Flow<List<Label>> =
        labelDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun create(name: String, color: Long): String {
        val id = UUID.randomUUID().toString()
        val all = labelDao.getAll()
        val maxSortOrder = all.maxOfOrNull { it.sortOrder } ?: 0
        labelDao.insert(
            LabelEntity(
                id = id,
                name = name,
                color = color,
                sortOrder = maxSortOrder + 1,
            ),
        )
        return id
    }

    override suspend fun update(label: Label) {
        labelDao.update(
            LabelEntity(
                id = label.id,
                name = label.name,
                color = label.color,
                sortOrder = label.sortOrder,
            ),
        )
    }

    override suspend fun delete(labelId: String) {
        val entity = labelDao.getById(labelId) ?: return
        labelDao.delete(entity)
    }
}
