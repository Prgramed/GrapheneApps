package dev.egallery.data.repository

import dev.egallery.domain.model.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun observeAll(): Flow<List<Album>>
    suspend fun getById(id: String): Album?
    suspend fun create(name: String): Result<Album>
    suspend fun rename(id: String, name: String): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
