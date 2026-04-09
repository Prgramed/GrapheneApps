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
) : ViewModel() {

    val trashItems: Flow<List<MediaItem>> = mediaDao.getTrash().distinctUntilChanged().map { entities ->
        entities.map { it.toDomain() }
    }

    fun restore(nasId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaDao.restore(nasId, "SYNCED")
        }
    }

    fun permanentDelete(nasId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mediaDao.getByNasId(nasId)
            entity?.localPath?.let { storageManager.deleteLocalFile(it) }
            mediaDao.permanentDelete(nasId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
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
