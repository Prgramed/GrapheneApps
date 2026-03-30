package dev.egallery.ui.viewer

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.repository.MediaRepository
import dev.egallery.data.repository.toEntity
import dev.egallery.domain.model.LocalExpiry
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.StorageStatus
import dev.egallery.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val mediaDao: MediaDao,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
    private val storageManager: StorageManager,
) : ViewModel() {

    private val nasId: String = savedStateHandle["nasId"] ?: ""

    private val _item = MutableStateFlow<MediaItem?>(null)
    val item: StateFlow<MediaItem?> = _item.asStateFlow()

    private val _playerUri = MutableStateFlow<Uri?>(null)
    val playerUri: StateFlow<Uri?> = _playerUri.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        viewModelScope.launch {
            val mediaItem = mediaRepository.getItemDetail(nasId) ?: return@launch
            _item.value = mediaItem

            when (mediaItem.storageStatus) {
                StorageStatus.ON_DEVICE -> {
                    mediaItem.localPath?.let { _playerUri.value = File(it).toUri() }
                }
                StorageStatus.NAS_ONLY -> downloadAndPlay(mediaItem)
                else -> {
                    // UPLOAD_PENDING / UPLOAD_FAILED — try local path
                    mediaItem.localPath?.let { _playerUri.value = File(it).toUri() }
                }
            }
        }
    }

    private fun downloadAndPlay(mediaItem: MediaItem) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0f)
            try {
                withContext(Dispatchers.IO) {
                    val response = immichApi.downloadOriginal(nasId)
                    val destFile = storageManager.localFilePath(nasId, mediaItem.filename)
                    val totalBytes = response.contentLength()

                    response.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Long = 0
                            var read: Int

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (totalBytes > 0) {
                                    _downloadState.value = DownloadState.Downloading(
                                        bytesRead.toFloat() / totalBytes,
                                    )
                                }
                            }
                        }
                    }

                    val thirtyDays = 30L * 24 * 60 * 60 * 1000
                    val updated = mediaItem.copy(
                        storageStatus = StorageStatus.ON_DEVICE,
                        localPath = destFile.absolutePath,
                        localExpiry = LocalExpiry.Fixed(System.currentTimeMillis() + thirtyDays),
                    )
                    mediaDao.upsert(updated.toEntity())
                    _item.value = updated
                    _playerUri.value = destFile.toUri()
                }
                _downloadState.value = DownloadState.Done
                Timber.d("Video downloaded, ready to play: $nasId")
            } catch (e: Exception) {
                Timber.e(e, "Video download failed for $nasId")
                _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }
}
