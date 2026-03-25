package dev.emusic.ui.internetradio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.domain.model.RadioStation
import dev.emusic.domain.repository.InternetRadioRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioNowPlayingBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountryStationsUiState(
    val stations: List<RadioStation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CountryStationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    application: Application,
    private val radioRepository: InternetRadioRepository,
    private val queueManager: QueueManager,
    private val radioNowPlayingBridge: RadioNowPlayingBridge,
    sessionToken: SessionToken,
) : AndroidViewModel(application) {

    val countryCode: String = savedStateHandle["countryCode"] ?: ""
    val countryName: String = savedStateHandle["countryName"] ?: ""

    private val _uiState = MutableStateFlow(CountryStationsUiState())
    val uiState: StateFlow<CountryStationsUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

    init {
        controllerFuture.addListener({
            controller = controllerFuture.get()
        }, MoreExecutors.directExecutor())

        loadStations()
    }

    private fun loadStations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val stations = radioRepository.getStationsByCountry(countryCode)
                _uiState.value = CountryStationsUiState(stations = stations)
            } catch (e: Exception) {
                _uiState.value = CountryStationsUiState(error = "Failed to load stations")
            }
        }
    }

    fun refresh() {
        loadStations()
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
        _uiState.update { state ->
            state.copy(stations = state.stations.map { if (it.stationUuid == station.stationUuid) updated else it })
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
