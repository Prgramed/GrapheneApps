package dev.emusic.ui.library.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.AlbumInfo
import dev.emusic.domain.model.Track
import dev.emusic.data.download.DownloadManager
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    val queueManager: QueueManager,
    private val downloadManager: DownloadManager,
    private val radioEngine: RadioEngine,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""

    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _albumInfo = MutableStateFlow<AlbumInfo?>(null)
    val albumInfo: StateFlow<AlbumInfo?> = _albumInfo.asStateFlow()

    private val _moreByArtist = MutableStateFlow<List<Album>>(emptyList())
    val moreByArtist: StateFlow<List<Album>> = _moreByArtist.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val albumData = libraryRepository.getAlbum(albumId)
            _album.value = albumData

            // Sync tracks from API into Room
            launch {
                try {
                    libraryRepository.syncAlbumTracks(albumId)
                } catch (_: Exception) { }
            }

            // Observe tracks from Room
            launch {
                libraryRepository.observeTracksByAlbum(albumId).collect {
                    _tracks.value = it
                }
            }

            // Load album info
            launch {
                _albumInfo.value = libraryRepository.getAlbumInfo(albumId)
            }

            // Load more by artist
            launch {
                val artistId = albumData?.artistId ?: return@launch
                libraryRepository.observeAlbumsByArtist(artistId).collect { albums ->
                    _moreByArtist.value = albums.filter { it.id != albumId }
                }
            }

            _isLoading.value = false
        }
    }

    fun getCoverArtUrl(id: String, size: Int = 300): String =
        libraryRepository.getCoverArtUrl(id, size)

    fun playAlbumFromTrack(trackId: String) {
        val trackList = _tracks.value
        val index = trackList.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        if (trackList.isNotEmpty()) {
            queueManager.play(trackList, index)
        }
    }

    fun shuffleAlbum() {
        val trackList = _tracks.value.ifEmpty { return }
        queueManager.play(trackList.shuffled(), 0)
    }

    val isPinned: StateFlow<Boolean> = albumDao.observeAll()
        .map { all -> all.find { it.id == albumId }?.pinned ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _downloadProgress = MutableStateFlow("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeTracksByAlbum(albumId).collect { tracks ->
                val total = tracks.size
                val done = tracks.count { it.localPath != null }
                _downloadProgress.value = when {
                    total == 0 -> ""
                    done >= total -> "All downloaded"
                    done > 0 -> "$done / $total downloaded"
                    else -> ""
                }
            }
        }
    }

    fun togglePinned() {
        viewModelScope.launch {
            val current = isPinned.value
            albumDao.setPinned(albumId, !current)
            if (current) {
                val tracks = _tracks.value
                for (track in tracks) { downloadManager.cancel(track.id) }
            }
        }
    }

    fun removeDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            albumDao.setPinned(albumId, false)
            val tracks = _tracks.value
            for (track in tracks) {
                val entity = trackDao.getById(track.id) ?: continue
                val path = entity.localPath ?: continue
                try { java.io.File(path).delete() } catch (_: Exception) { }
                trackDao.updateLocalPath(track.id, null)
                downloadManager.cancel(track.id)
            }
        }
    }

    fun toggleStar(track: Track) = toggleStar(track.id, track.starred)

    fun toggleStar(trackId: String, currentlyStarred: Boolean) {
        viewModelScope.launch {
            if (currentlyStarred) {
                libraryRepository.unstarTrack(trackId)
            } else {
                libraryRepository.starTrack(trackId)
            }
        }
    }

    fun rateTrack(trackId: String, rating: Int) {
        viewModelScope.launch {
            libraryRepository.setRating(trackId, rating)
        }
    }

    fun startRadioFromTrack(track: Track) {
        radioEngine.startFromTrack(track)
    }
}
