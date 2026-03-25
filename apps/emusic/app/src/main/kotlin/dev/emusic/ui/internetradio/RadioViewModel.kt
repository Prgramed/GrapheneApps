package dev.emusic.ui.internetradio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.entity.CountryEntity
import dev.emusic.domain.model.RadioStation
import dev.emusic.domain.repository.InternetRadioRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioNowPlayingBridge
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioBrowseUiState(
    val topStations: List<RadioStation> = emptyList(),
    val topClickedStations: List<RadioStation> = emptyList(),
    val favourites: List<RadioStation> = emptyList(),
    val countries: List<CountryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class RadioSearchUiState(
    val results: List<RadioStation> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class RadioViewModel @Inject constructor(
    application: Application,
    private val radioRepository: InternetRadioRepository,
    private val queueManager: QueueManager,
    private val radioNowPlayingBridge: RadioNowPlayingBridge,
    sessionToken: SessionToken,
) : AndroidViewModel(application) {

    private val _browseState = MutableStateFlow(RadioBrowseUiState())
    val browseState: StateFlow<RadioBrowseUiState> = _browseState.asStateFlow()

    val searchQuery = MutableStateFlow("")

    private val _searchState = MutableStateFlow(RadioSearchUiState())
    val searchState: StateFlow<RadioSearchUiState> = _searchState.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

    init {
        controllerFuture.addListener({
            controller = controllerFuture.get()
        }, MoreExecutors.directExecutor())

        loadBrowseData()

        viewModelScope.launch {
            radioRepository.observeFavourites().collect { favs ->
                _browseState.update { it.copy(favourites = favs) }
            }
        }

        viewModelScope.launch {
            radioRepository.observeCountries().collect { countries ->
                _browseState.update { it.copy(countries = countries) }
            }
        }

        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _searchState.value = RadioSearchUiState()
                    } else {
                        performSearch(q)
                    }
                }
        }
    }

    private fun loadBrowseData() {
        viewModelScope.launch {
            _browseState.update { it.copy(isLoading = true) }
            try {
                val topVoted = radioRepository.getTopVoted(20)
                val topClicked = radioRepository.getTopClicked(20)
                _browseState.update {
                    it.copy(
                        topStations = topVoted,
                        topClickedStations = topClicked,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _browseState.update { it.copy(isLoading = false, error = "Failed to load stations") }
            }
        }
        viewModelScope.launch {
            try {
                radioRepository.syncCountries()
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        loadBrowseData()
    }

    private suspend fun performSearch(query: String) {
        _searchState.update { it.copy(isLoading = true) }
        try {
            val results = if (query.startsWith("#")) {
                radioRepository.getStationsByTag(query.removePrefix("#").trim())
            } else {
                radioRepository.searchStations(query)
            }
            _searchState.value = RadioSearchUiState(
                results = results,
                isLoading = false,
                hasSearched = true,
            )
        } catch (_: Exception) {
            _searchState.value = RadioSearchUiState(
                isLoading = false,
                hasSearched = true,
            )
        }
    }

    fun playStation(station: RadioStation) {
        val mc = controller ?: return

        queueManager.setLiveStream(true)
        queueManager.clear()

        val mediaItem = MediaItem.Builder()
            .setMediaId("radio_${station.stationUuid}")
            .setUri(station.urlResolved ?: station.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.country ?: "")
                    .build(),
            )
            .build()

        mc.setMediaItem(mediaItem)
        mc.prepare()
        mc.play()

        radioNowPlayingBridge.onStationStarted(station)

        viewModelScope.launch {
            try {
                radioRepository.reportClick(station.stationUuid)
                radioRepository.updateLastPlayed(station.stationUuid)
            } catch (_: Exception) { }
        }
    }

    fun toggleFavourite(station: RadioStation) {
        val newFav = !station.isFavourite
        val updated = station.copy(isFavourite = newFav)
        _browseState.update { state ->
            state.copy(topStations = state.topStations.map { if (it.stationUuid == station.stationUuid) updated else it })
        }
        _searchState.update { state ->
            state.copy(results = state.results.map { if (it.stationUuid == station.stationUuid) updated else it })
        }
        viewModelScope.launch {
            if (newFav) {
                radioRepository.addFavourite(station)
            } else {
                radioRepository.removeFavourite(station.stationUuid)
            }
        }
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
