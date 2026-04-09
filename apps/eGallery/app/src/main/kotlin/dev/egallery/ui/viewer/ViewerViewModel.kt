package dev.egallery.ui.viewer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.MediaRepository
import dev.egallery.data.repository.toEntity
import dev.egallery.domain.model.LocalExpiry
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.StorageStatus
import dev.egallery.util.StorageManager
import dev.egallery.util.ThumbnailUrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Done : DownloadState
    data class Error(val message: String) : DownloadState
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val mediaRepository: MediaRepository,
    private val mediaDao: MediaDao,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val storageManager: StorageManager,
) : ViewModel() {

    val initialNasId: String = savedStateHandle["nasId"] ?: ""

    private val _timelineIds = MutableStateFlow<List<String>>(emptyList())
    val timelineIds: StateFlow<List<String>> = _timelineIds.asStateFlow()

    private val _currentItem = MutableStateFlow<MediaItem?>(null)
    val currentItem: StateFlow<MediaItem?> = _currentItem.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // Fix #2: track download job for cancellation on page swipe
    private var downloadJob: Job? = null

    // Fix #1: one-shot delete event (consumed after read)
    private val _deleteEvent = MutableStateFlow(false)
    val deleteEvent: StateFlow<Boolean> = _deleteEvent.asStateFlow()

    fun consumeDeleteEvent() {
        _deleteEvent.value = false
    }

    // EXIF data for info sheet
    private val _exifData = MutableStateFlow<ExifData?>(null)
    val exifData: StateFlow<ExifData?> = _exifData.asStateFlow()

    fun loadExifData() {
        val item = _currentItem.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Try local file first
            if (item.localPath != null && !item.localPath.startsWith("content://")) {
                val data = ExifData.fromLocalFile(item.localPath)
                if (data != null) {
                    _exifData.value = data
                    return@launch
                }
            }
            // Try Immich API for EXIF
            if (item.nasId.isNotBlank() && credentialStore.serverUrl.isNotBlank()) {
                try {
                    val asset = immichApi.getAsset(item.nasId)
                    if (asset.exifInfo != null) {
                        _exifData.value = ExifData.fromImmichExifInfo(asset.exifInfo)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load EXIF from server")
                }
            }
        }
    }

    // Fast lookup for local paths — populated alongside timeline IDs
    private val entryMap = mutableMapOf<String, dev.egallery.data.db.dao.ViewerEntry>()

    init {
        // Show current photo immediately
        viewModelScope.launch {
            _currentItem.value = mediaRepository.getItemDetail(initialNasId)
        }
        // Load lightweight viewer entries for all timeline items
        viewModelScope.launch(Dispatchers.IO) {
            val entries = mediaDao.getViewerEntries()
            for (entry in entries) {
                entryMap[entry.nasId] = entry
            }
            _timelineIds.value = entries.map { it.nasId }
        }
    }

    fun loadItem(nasId: String) {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
        _exifData.value = null

        viewModelScope.launch {
            _currentItem.value = mediaRepository.getItemDetail(nasId)
        }
    }

    fun resolveImage(nasId: String): Any {
        val entry = entryMap[nasId]
        if (entry != null) {
            val isVideo = entry.mediaType == "VIDEO"
            if (!isVideo && entry.localPath != null) {
                if (entry.localPath.startsWith("content://")) return android.net.Uri.parse(entry.localPath)
                val file = File(entry.localPath)
                if (file.exists()) return file
            }
            // Videos: use the same thumbnail URL the timeline grid already cached
            if (isVideo && credentialStore.serverUrl.isNotBlank() && nasId.length > 10) {
                return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
            }
        }
        // Fallback: server thumbnail (likely already in Coil cache from timeline)
        if (credentialStore.serverUrl.isNotBlank() && nasId.length > 10) {
            return ThumbnailUrlBuilder.thumbnail(credentialStore.serverUrl, nasId)
        }
        return ""
    }

    fun imageUrl(item: MediaItem): Any {
        // Videos: always use server thumbnail (Coil can't decode video files as images)
        if (item.mediaType == dev.egallery.domain.model.MediaType.VIDEO) {
            if (credentialStore.serverUrl.isNotBlank()) {
                return ThumbnailUrlBuilder.preview(credentialStore.serverUrl, item.nasId)
            }
            // Fallback: local video file (Coil's VideoFrameDecoder can extract a frame)
            if (item.localPath != null && !item.localPath.startsWith("content://")) {
                val file = File(item.localPath)
                if (file.exists()) return file
            }
            return ""
        }

        if (item.localPath != null) {
            if (item.localPath.startsWith("content://")) {
                return android.net.Uri.parse(item.localPath)
            }
            val file = File(item.localPath)
            if (file.exists()) return file
            if (item.storageStatus == StorageStatus.SYNCED) {
                viewModelScope.launch {
                    mediaDao.updateStorageStatus(item.nasId, "NAS", null)
                }
            }
        }
        if (credentialStore.serverUrl.isNotBlank()) {
            return ThumbnailUrlBuilder.original(credentialStore.serverUrl, item.nasId)
        }
        return ""
    }

    fun downloadForOffline(nasId: String) {
        // Fix #2: cancel previous download, store new job
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0f)
            try {
                val item = mediaRepository.getItemDetail(nasId) ?: run {
                    _downloadState.value = DownloadState.Error("Item not found")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val response = immichApi.downloadOriginal(nasId)
                    val destFile = storageManager.localFilePath(nasId, item.filename)
                    val totalBytes = response.contentLength()

                    response.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Long = 0
                            var read: Int

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                _downloadState.value = if (totalBytes > 0) {
                                    DownloadState.Downloading(bytesRead.toFloat() / totalBytes)
                                } else {
                                    DownloadState.Downloading(-1f) // indeterminate
                                }
                            }
                        }
                    }

                    val thirtyDays = 30L * 24 * 60 * 60 * 1000
                    val updated = item.copy(
                        storageStatus = StorageStatus.SYNCED,
                        localPath = destFile.absolutePath,
                        localExpiry = LocalExpiry.Fixed(System.currentTimeMillis() + thirtyDays),
                    )
                    mediaDao.upsert(updated.toEntity())
                    _currentItem.value = updated
                }

                _downloadState.value = DownloadState.Done
                Timber.d("Downloaded $nasId to local storage")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Download failed for $nasId")
                _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    // Fix #5: share handles NAS_ONLY by downloading to temp first
    fun shareCurrentItem() {
        val item = _currentItem.value ?: return

        viewModelScope.launch {
            try {
                val fileToShare: File = if (item.localPath != null && !item.localPath.startsWith("content://")) {
                    val f = File(item.localPath)
                    if (f.exists()) f else downloadToTemp(item) ?: return@launch
                } else if (item.localPath != null && item.localPath.startsWith("content://")) {
                    // Content URI — copy to temp file for sharing
                    val tempDir = File(appContext.cacheDir, "share_temp").also { it.mkdirs() }
                    val tempFile = File(tempDir, item.filename)
                    appContext.contentResolver.openInputStream(android.net.Uri.parse(item.localPath))?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                } else {
                    // NAS_ONLY: download to temp
                    downloadToTemp(item) ?: return@launch
                }

                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    fileToShare,
                )
                val mimeType = if (item.mediaType == dev.egallery.domain.model.MediaType.VIDEO) "video/*" else "image/*"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(Intent.createChooser(shareIntent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Timber.e(e, "Failed to share item")
            }
        }
    }

    private suspend fun downloadToTemp(item: MediaItem): File? {
        return withContext(Dispatchers.IO) {
            try {
                val response = immichApi.downloadOriginal(item.nasId)
                val tempDir = File(appContext.cacheDir, "share_temp").also { it.mkdirs() }
                val tempFile = File(tempDir, item.filename)
                response.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile
            } catch (e: Exception) {
                Timber.e(e, "Failed to download for share")
                null
            }
        }
    }

    // Move to trash (locally + on Immich server)
    fun deleteCurrentItem() {
        val item = _currentItem.value ?: return
        viewModelScope.launch {
            try {
                mediaDao.trash(item.nasId, System.currentTimeMillis())
                _timelineIds.value = _timelineIds.value.filter { it != item.nasId }
                _deleteEvent.value = true

                // Also trash on Immich server (retry up to 3 times)
                if (item.nasId.length > 10 && !item.nasId.startsWith("-")) {
                    for (attempt in 1..3) {
                        try {
                            immichApi.deleteAssets(kotlinx.serialization.json.buildJsonObject {
                                put("ids", kotlinx.serialization.json.JsonArray(
                                    listOf(kotlinx.serialization.json.JsonPrimitive(item.nasId))
                                ))
                            })
                            break
                        } catch (e: Exception) {
                            if (attempt == 3) Timber.w(e, "Failed to delete ${item.nasId} on server after 3 attempts")
                            else kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                }
                Timber.d("Trashed item: ${item.nasId}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to trash item")
            }
        }
    }

    fun toggleFavorite() {
        val item = _currentItem.value ?: return
        viewModelScope.launch {
            val newFav = !item.isFavorite
            mediaDao.setFavorite(item.nasId, newFav)
            _currentItem.value = item.copy(isFavorite = newFav)
        }
    }

    fun getDeviceFolders(): List<String> {
        val dirs = mutableSetOf<String>()
        // Standard Android media directories
        listOf("DCIM/Camera", "Pictures", "Pictures/Screenshots", "Download", "Movies")
            .map { "/storage/emulated/0/$it" }
            .filter { java.io.File(it).exists() }
            .forEach { dirs.add(it) }
        return dirs.sorted()
    }

    fun copyCurrentTo(destDir: String) {
        val item = _currentItem.value ?: return
        val srcPath = item.localPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val srcFile = if (srcPath.startsWith("content://")) {
                    // Copy content URI to temp, then to dest
                    val tempFile = File(appContext.cacheDir, "copy_temp_${item.filename}")
                    appContext.contentResolver.openInputStream(android.net.Uri.parse(srcPath))?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                } else {
                    File(srcPath)
                }
                if (!srcFile.exists()) return@launch

                val destFile = File(destDir, item.filename)
                destFile.parentFile?.mkdirs()
                srcFile.copyTo(destFile, overwrite = true)

                // Insert copy into Room
                val tempNasId = "local-${java.util.UUID.randomUUID()}"
                val newEntity = dev.egallery.data.db.entity.MediaEntity(
                    nasId = tempNasId,
                    filename = item.filename,
                    captureDate = item.captureDate,
                    fileSize = destFile.length(),
                    mediaType = item.mediaType.name,
                    folderId = 0,
                    cacheKey = "",
                    localPath = destFile.absolutePath,
                    storageStatus = "SYNCED",
                    lastSyncedAt = System.currentTimeMillis(),
                )
                mediaDao.upsert(newEntity)
                Timber.d("Copied ${item.filename} to $destDir")
            } catch (e: Exception) {
                Timber.e(e, "Copy failed")
            }
        }
    }

    fun moveCurrentTo(destDir: String) {
        val item = _currentItem.value ?: return
        val srcPath = item.localPath ?: return
        if (srcPath.startsWith("content://")) return // can't move content URIs

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val srcFile = File(srcPath)
                if (!srcFile.exists()) return@launch

                val destFile = File(destDir, item.filename)
                destFile.parentFile?.mkdirs()
                srcFile.copyTo(destFile, overwrite = true)
                srcFile.delete()

                // Update Room entry with new path
                mediaDao.updateStorageStatus(item.nasId, "SYNCED", destFile.absolutePath)
                _currentItem.value = item.copy(localPath = destFile.absolutePath)
                Timber.d("Moved ${item.filename} to $destDir")
            } catch (e: Exception) {
                Timber.e(e, "Move failed")
            }
        }
    }
}
