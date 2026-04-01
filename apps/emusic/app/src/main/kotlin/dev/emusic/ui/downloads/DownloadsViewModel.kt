package dev.emusic.ui.downloads

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.data.download.DownloadManager
import dev.emusic.data.download.StoragePaths
import dev.emusic.domain.model.DownloadState
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DownloadedAlbum(
    val albumId: String,
    val albumName: String,
    val artistName: String,
    val coverArtId: String?,
    val trackCount: Int,
    val totalSizeBytes: Long,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val downloadManager: DownloadManager,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _downloadedAlbums = MutableStateFlow<List<DownloadedAlbum>>(emptyList())
    val downloadedAlbums: StateFlow<List<DownloadedAlbum>> = _downloadedAlbums.asStateFlow()

    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    private val _storageAvailable = MutableStateFlow(0L)
    val storageAvailable: StateFlow<Long> = _storageAvailable.asStateFlow()

    val activeDownloads: StateFlow<List<Pair<String, DownloadState>>> = downloadManager.observeAll()
        .map { all -> all.filter { (_, state) -> state is DownloadState.Queued || state is DownloadState.Downloading } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            trackDao.observeDownloaded().collect { tracks ->
                val domainTracks = tracks.map { it.toDomain() }
                _downloadedAlbums.value = domainTracks
                    .groupBy { it.albumId }
                    .map { (albumId, albumTracks) ->
                        val first = albumTracks.first()
                        val totalSize = albumTracks.sumOf { track ->
                            track.localPath?.let { File(it).length() } ?: 0L
                        }
                        DownloadedAlbum(
                            albumId = albumId,
                            albumName = first.album,
                            artistName = first.artist,
                            coverArtId = albumId,
                            trackCount = albumTracks.size,
                            totalSizeBytes = totalSize,
                        )
                    }
                refreshStorageInfo()
            }
        }
    }

    fun getCoverArtUrl(id: String): String =
        libraryRepository.getCoverArtUrl(id)

    fun cancelDownload(trackId: String) {
        downloadManager.cancel(trackId)
    }

    fun removeAlbumDownloads(albumId: String) {
        viewModelScope.launch {
            downloadManager.cancelAlbum(albumId)
            trackDao.observeByAlbum(albumId).collect { tracks ->
                for (track in tracks) {
                    track.localPath?.let { path -> File(path).delete() }
                    trackDao.updateLocalPath(track.id, null)
                }
                return@collect
            }
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            File(context.filesDir, "downloads").deleteRecursively()
            File(context.filesDir, "artwork").deleteRecursively()
            trackDao.clearAllLocalPaths()
        }
    }

    private fun refreshStorageInfo() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val downloadsDir = File(context.filesDir, "downloads")
            _storageUsed.value = downloadsDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            val stat = StatFs(context.filesDir.absolutePath)
            _storageAvailable.value = stat.availableBytes
        }
    }
}
