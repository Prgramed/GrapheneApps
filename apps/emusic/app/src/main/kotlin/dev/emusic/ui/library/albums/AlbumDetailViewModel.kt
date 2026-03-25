package dev.emusic.ui.library.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.AlbumInfo
import dev.emusic.domain.model.Track
import dev.emusic.data.download.DownloadManager
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    val queueManager: QueueManager,
    private val downloadManager: DownloadManager,
    private val radioEngine: RadioEngine,
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

    fun playAlbumFromTrack(index: Int) {
        val trackList = _tracks.value
        if (trackList.isNotEmpty()) {
            queueManager.play(trackList, index)
        }
    }

    fun shuffleAlbum() {
        val trackList = _tracks.value.ifEmpty { return }
        queueManager.play(trackList.shuffled(), 0)
    }

    fun downloadAlbum() {
        val trackList = _tracks.value.ifEmpty { return }
        viewModelScope.launch { downloadManager.enqueueAlbum(trackList) }
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
