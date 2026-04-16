package dev.egallery.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.toDomain
import dev.egallery.domain.model.MediaItem
import dev.egallery.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val storageManager: StorageManager,
    private val credentialStore: CredentialStore,
    private val immichApi: dev.egallery.api.ImmichPhotoService,
) : ViewModel() {

    val trashItems: Flow<List<MediaItem>> = mediaDao.getTrash().distinctUntilChanged().map { entities ->
        entities.map { it.toDomain() }
    }

    fun restore(nasId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mediaDao.getByNasId(nasId)
            val status = if (entity?.localPath != null) "SYNCED" else "NAS"
            mediaDao.restore(nasId, status)
        }
    }

    fun permanentDelete(nasId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mediaDao.getByNasId(nasId)
            entity?.localPath?.let { storageManager.deleteLocalFile(it) }
            // Delete from Immich server too (only for real server IDs, not temp negative IDs)
            if (nasId.length > 10 && !nasId.startsWith("-")) {
                try {
                    immichApi.deleteAssets(kotlinx.serialization.json.buildJsonObject {
                        put("ids", kotlinx.serialization.json.JsonArray(
                            listOf(kotlinx.serialization.json.JsonPrimitive(nasId)),
                        ))
                        put("force", kotlinx.serialization.json.JsonPrimitive(true))
                    })
                } catch (_: Exception) { }
            }
            mediaDao.permanentDelete(nasId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete from Immich server first (only real server IDs)
            val trashed = mediaDao.getExpiredTrash(Long.MAX_VALUE)
            val serverIds = trashed
                .filter { it.nasId.length > 10 && !it.nasId.startsWith("-") }
                .map { it.nasId }
            if (serverIds.isNotEmpty()) {
                try {
                    immichApi.deleteAssets(kotlinx.serialization.json.buildJsonObject {
                        put("ids", kotlinx.serialization.json.JsonArray(
                            serverIds.map { kotlinx.serialization.json.JsonPrimitive(it) },
                        ))
                        put("force", kotlinx.serialization.json.JsonPrimitive(true))
                    })
                } catch (_: Exception) { }
            }
            // Delete local files
            for (entity in trashed) {
                entity.localPath?.let { storageManager.deleteLocalFile(it) }
            }
            // Use batch delete to avoid CursorWindow overflow on large trash sets
            mediaDao.emptyTrash()
        }
    }

    fun restoreAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = mediaDao.restoreAllTrash()
            timber.log.Timber.d("Restored $count items from trash")
        }
    }

    fun thumbnailModel(item: MediaItem): Any {
        if (item.localPath != null) {
            if (item.localPath.startsWith("content://")) return android.net.Uri.parse(item.localPath)
            val file = java.io.File(item.localPath)
            if (file.exists()) return file
        }
        if (credentialStore.serverUrl.isNotBlank()) {
            return dev.egallery.util.ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, item.nasId)
        }
        return ""
    }
}
