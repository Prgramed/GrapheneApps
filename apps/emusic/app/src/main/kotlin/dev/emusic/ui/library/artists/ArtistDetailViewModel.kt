package dev.emusic.ui.library.artists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.ArtistInfo
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    val queueManager: QueueManager,
    private val radioEngine: RadioEngine,
) : ViewModel() {

    private val artistId: String = savedStateHandle["artistId"] ?: ""

    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    private val _artistInfo = MutableStateFlow<ArtistInfo?>(null)
    val artistInfo: StateFlow<ArtistInfo?> = _artistInfo.asStateFlow()

    private val _topSongs = MutableStateFlow<List<Track>>(emptyList())
    val topSongs: StateFlow<List<Track>> = _topSongs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val artistData = libraryRepository.getArtist(artistId)
            _artist.value = artistData

            // Load in parallel
            launch {
                _artistInfo.value = libraryRepository.getArtistInfo(artistId)
            }
            launch {
                val name = artistData?.name ?: return@launch
                _topSongs.value = libraryRepository.getTopSongs(name)
            }
            launch {
                libraryRepository.observeAlbumsByArtist(artistId).collect {
                    _albums.value = it
                }
            }

            _isLoading.value = false
        }
    }

    fun getCoverArtUrl(id: String, size: Int = 300): String =
        libraryRepository.getCoverArtUrl(id, size)

    fun playTrack(track: Track) {
        queueManager.play(listOf(track), 0)
    }

    fun startRadio() {
        val artist = _artist.value ?: return
        radioEngine.startFromArtist(artist.id, artist.name)
    }

    fun toggleStar(track: Track) {
        viewModelScope.launch {
            if (track.starred) libraryRepository.unstarTrack(track.id)
            else libraryRepository.starTrack(track.id)
        }
    }

    fun shuffleAll() {
        val songs = _topSongs.value.ifEmpty { return }
        val shuffled = songs.shuffled()
        queueManager.play(shuffled, 0)
    }
}
