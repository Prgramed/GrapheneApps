package com.prgramed.edoist.data.repository

import com.prgramed.edoist.data.database.dao.FilterDao
import com.prgramed.edoist.data.database.entity.FilterEntity
import com.prgramed.edoist.domain.model.Filter
import com.prgramed.edoist.domain.model.FilterQuery
import com.prgramed.edoist.domain.model.Priority
import com.prgramed.edoist.domain.repository.FilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterRepositoryImpl @Inject constructor(
    private val filterDao: FilterDao,
) : FilterRepository {

    // ── Serialization ──────────────────────────────────────────────────────
    //
    // Format: "p:1,2|l:id1,id2|proj:id1,id2|d:TODAY|nd:1|s:text"
    //   p     = priorities (int values)
    //   l     = label IDs
    //   proj  = project IDs
    //   d     = due date range enum name
    //   nd    = include no date (1/0)
    //   s     = search text

    private fun FilterQuery.serialize(): String = buildString {
        if (priorities.isNotEmpty()) {
            append("p:")
            append(priorities.joinToString(",") { it.value.toString() })
        }
        if (labelIds.isNotEmpty()) {
            if (isNotEmpty()) append("|")
            append("l:")
            append(labelIds.joinToString(","))
        }
        if (projectIds.isNotEmpty()) {
            if (isNotEmpty()) append("|")
            append("proj:")
            append(projectIds.joinToString(","))
        }
        val range = dueDateRange
        if (range != null) {
            if (isNotEmpty()) append("|")
            append("d:")
            append(range.name)
        }
        if (includeNoDate) {
            if (isNotEmpty()) append("|")
            append("nd:1")
        }
        if (!searchText.isNullOrEmpty()) {
            if (isNotEmpty()) append("|")
            append("s:")
            append(searchText)
        }
    }

    private fun deserializeQuery(raw: String): FilterQuery {
        if (raw.isBlank()) return FilterQuery()

        val parts = raw.split("|").associate { part ->
            val colonIndex = part.indexOf(':')
            if (colonIndex < 0) return@associate part to ""
            part.substring(0, colonIndex) to part.substring(colonIndex + 1)
        }

        return FilterQuery(
            priorities = parts["p"]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { it.toIntOrNull() }
                ?.map { Priority.fromValue(it) }
                ?.toSet()
                ?: emptySet(),
            labelIds = parts["l"]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            projectIds = parts["proj"]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            dueDateRange = parts["d"]?.let {
                runCatching { FilterQuery.DueDateRange.valueOf(it) }.getOrNull()
            },
            includeNoDate = parts["nd"] == "1",
            searchText = parts["s"]?.ifEmpty { null },
        )
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private fun FilterEntity.toDomain(): Filter = Filter(
        id = id,
        name = name,
        iconName = iconName.ifEmpty { null },
        color = color,
        query = deserializeQuery(query),
        sortOrder = sortOrder,
    )

    // ── Repository ─────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<Filter>> =
        filterDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun create(filter: Filter): String {
        val id = UUID.randomUUID().toString()
        filterDao.insert(
            FilterEntity(
                id = id,
                name = filter.name,
                iconName = filter.iconName ?: "",
                color = filter.color,
                query = filter.query.serialize(),
                sortOrder = filter.sortOrder,
            ),
        )
        return id
    }

    override suspend fun update(filter: Filter) {
        filterDao.update(
            FilterEntity(
                id = filter.id,
                name = filter.name,
                iconName = filter.iconName ?: "",
                color = filter.color,
                query = filter.query.serialize(),
                sortOrder = filter.sortOrder,
            ),
        )
    }

    override suspend fun delete(filterId: String) {
        val entity = filterDao.getById(filterId) ?: return
        filterDao.delete(entity)
    }
}
