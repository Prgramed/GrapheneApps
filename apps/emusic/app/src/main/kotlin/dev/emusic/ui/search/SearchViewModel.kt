package dev.emusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.api.toDomain
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.data.preferences.NetworkMonitor
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.QueueManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: SubsonicApiService,
    private val trackDao: TrackDao,
    private val libraryRepository: LibraryRepository,
    private val networkMonitor: NetworkMonitor,
    val queueManager: QueueManager,
) : ViewModel() {

    val query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .debounce(250)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _uiState.value = SearchUiState()
                    } else {
                        performSearch(q)
                    }
                }
        }
    }

    private suspend fun performSearch(q: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        try { coroutineScope {
            val ftsQuery = escapeFtsQuery(q) + "*"
            val ftsResult = async {
                try {
                    trackDao.searchFts(ftsQuery).first().map { it.toDomain() }
                } catch (_: Exception) { emptyList() }
            }

            val apiResult = async {
                if (networkMonitor.isOnline.value) {
                    try {
                        api.search3(q).subsonicResponse.searchResult3
                    } catch (_: Exception) { null }
                } else null
            }

            val ftsTracks = ftsResult.await()
            val apiData = apiResult.await()

            val apiArtists = apiData?.artist?.map { it.toDomain() } ?: emptyList()
            val apiAlbums = apiData?.album?.map { it.toDomain() } ?: emptyList()
            val apiTracks = apiData?.song?.map { it.toDomain() } ?: emptyList()

            // Merge tracks: FTS first, then API results not already in FTS
            val ftsIds = ftsTracks.map { it.id }.toSet()
            val mergedTracks = ftsTracks + apiTracks.filter { it.id !in ftsIds }

            _uiState.value = SearchUiState(
                artists = apiArtists.take(5),
                albums = apiAlbums.take(10),
                tracks = mergedTracks.take(20),
                isLoading = false,
                hasSearched = true,
            )
        }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
            timber.log.Timber.w(e, "Search failed for: $q")
        }
    }

    fun getCoverArtUrl(id: String): String = libraryRepository.getCoverArtUrl(id, 100)

    fun playTrack(track: Track) {
        queueManager.play(listOf(track), 0)
    }

    fun toggleStar(track: Track) {
        viewModelScope.launch {
            if (track.starred) libraryRepository.unstarTrack(track.id)
            else libraryRepository.starTrack(track.id)
        }
    }

    private fun escapeFtsQuery(input: String): String =
        input.replace("\"", " ")
            .replace("*", " ")
            .replace("(", " ")
            .replace(")", " ")
            .trim()
}
