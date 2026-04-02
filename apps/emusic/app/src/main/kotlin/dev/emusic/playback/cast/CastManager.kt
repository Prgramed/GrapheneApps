package dev.emusic.playback.cast

import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class CastState { IDLE, DISCOVERING, CONNECTING, PLAYING, PAUSED }

@Singleton
class CastManager @Inject constructor(
    private val deviceDiscovery: DeviceDiscovery,
    private val upnpController: UpnpController,
    private val urlBuilder: SubsonicUrlBuilder,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _activeDevice = MutableStateFlow<CastDevice?>(null)
    val activeDevice: StateFlow<CastDevice?> = _activeDevice.asStateFlow()

    private val _castState = MutableStateFlow(CastState.IDLE)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    val isCasting: Boolean get() = _activeDevice.value != null

    private var heartbeatJob: Job? = null

    private fun startHeartbeat(device: CastDevice) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (_activeDevice.value != null) {
                delay(10_000) // Check every 10 seconds
                try {
                    val reachable = upnpController.checkReachable(device.controlUrl)
                    if (!reachable) {
                        Timber.w("Cast device ${device.name} unreachable, disconnecting")
                        disconnect()
                        return@launch
                    }
                } catch (e: Exception) {
                    Timber.w("Heartbeat failed for ${device.name}: ${e.message}")
                    disconnect()
                    return@launch
                }
            }
        }
    }

    fun startDiscovery() {
        _castState.value = CastState.DISCOVERING
        scope.launch {
            val found = deviceDiscovery.discover()
            _devices.value = found
            if (_castState.value == CastState.DISCOVERING) {
                _castState.value = if (_activeDevice.value != null) CastState.PLAYING else CastState.IDLE
            }
            Timber.d("Found ${found.size} cast devices: ${found.map { it.name }}")
        }
    }

    fun castTrack(device: CastDevice, track: Track) {
        _activeDevice.value = device
        _castState.value = CastState.CONNECTING
        scope.launch {
            val streamUrl = urlBuilder.getStreamUrl(track.id)
            val success = upnpController.setUri(device.controlUrl, streamUrl, track.title, track.artist)
            if (success) {
                // Set speaker to safe default volume
                device.renderingControlUrl?.let { rcUrl ->
                    upnpController.setVolume(rcUrl, 20)
                }
                upnpController.play(device.controlUrl)
                _castState.value = CastState.PLAYING
                startHeartbeat(device)
            } else {
                _castState.value = CastState.IDLE
                _activeDevice.value = null
            }
        }
    }

    fun castUrl(streamUrl: String, title: String = "", artist: String = "") {
        val device = _activeDevice.value ?: return
        scope.launch {
            upnpController.setUri(device.controlUrl, streamUrl, title, artist)
            upnpController.play(device.controlUrl)
            _castState.value = CastState.PLAYING
        }
    }

    fun play() {
        val device = _activeDevice.value ?: return
        scope.launch {
            upnpController.play(device.controlUrl)
            _castState.value = CastState.PLAYING
        }
    }

    fun pause() {
        val device = _activeDevice.value ?: return
        scope.launch {
            upnpController.pause(device.controlUrl)
            _castState.value = CastState.PAUSED
        }
    }

    fun stop() {
        val device = _activeDevice.value ?: return
        scope.launch {
            upnpController.stop(device.controlUrl)
        }
    }

    fun seek(positionMs: Long) {
        val device = _activeDevice.value ?: return
        scope.launch {
            upnpController.seek(device.controlUrl, positionMs)
        }
    }

    fun setVolume(volumePercent: Int) {
        val device = _activeDevice.value ?: return
        val rcUrl = device.renderingControlUrl ?: return
        scope.launch {
            upnpController.setVolume(rcUrl, volumePercent)
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        val device = _activeDevice.value
        if (device != null) {
            scope.launch {
                try { upnpController.stop(device.controlUrl) } catch (_: Exception) {}
            }
        }
        _activeDevice.value = null
        _castState.value = CastState.IDLE
    }

    fun onTrackChanged(track: Track) {
        if (!isCasting) return
        scope.launch {
            val device = _activeDevice.value ?: return@launch
            val streamUrl = urlBuilder.getStreamUrl(track.id)
            upnpController.setUri(device.controlUrl, streamUrl, track.title, track.artist)
            upnpController.play(device.controlUrl)
        }
    }
}
