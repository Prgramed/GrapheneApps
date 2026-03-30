package dev.egallery.data.repository

import androidx.paging.PagingData
import dev.egallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeTimeline(fromDate: Long? = null, toDate: Long? = null): Flow<PagingData<MediaItem>>
    fun observeFolder(folderId: Int): Flow<List<MediaItem>>
    fun observeAlbum(albumId: String): Flow<List<MediaItem>>
    suspend fun getItemDetail(nasId: String): MediaItem?
    suspend fun deleteFromNas(nasId: String): Result<Unit>
    suspend fun setTags(nasId: String, tagIds: List<String>): Result<Unit>
    suspend fun addToAlbum(nasId: String, albumId: String): Result<Unit>
    suspend fun getCount(): Int
}
