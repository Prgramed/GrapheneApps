package dev.emusic.playback

import dev.emusic.domain.model.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RadioStreamError {
    data class Reconnecting(val attempt: Int) : RadioStreamError
    data object Unavailable : RadioStreamError
}

@Singleton
class RadioNowPlayingBridge @Inject constructor() {

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _streamError = MutableStateFlow<RadioStreamError?>(null)
    val streamError: StateFlow<RadioStreamError?> = _streamError.asStateFlow()

    fun onStationStarted(station: RadioStation) {
        _currentStation.value = station
        _streamError.value = null
    }

    fun onStationStopped() {
        _currentStation.value = null
        _streamError.value = null
    }

    fun onStreamReconnecting(attempt: Int) {
        _streamError.value = RadioStreamError.Reconnecting(attempt)
    }

    fun onStreamFailed() {
        _streamError.value = RadioStreamError.Unavailable
    }

    fun onStreamRecovered() {
        _streamError.value = null
    }
}
