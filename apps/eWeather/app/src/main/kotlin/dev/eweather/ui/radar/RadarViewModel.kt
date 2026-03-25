package dev.eweather.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.eweather.data.api.OpenMeteoMapper
import dev.eweather.data.api.RainViewerService
import dev.eweather.data.preferences.AppPreferencesRepository
import dev.eweather.domain.model.RadarFrame
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val rainViewerService: RainViewerService,
    private val preferencesRepository: AppPreferencesRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _frames = MutableStateFlow<List<RadarFrame>>(emptyList())
    val frames: StateFlow<List<RadarFrame>> = _frames.asStateFlow()

    private val _currentFrameIndex = MutableStateFlow(0)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeLocation = MutableStateFlow<SavedLocation?>(null)
    val activeLocation: StateFlow<SavedLocation?> = _activeLocation.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var playbackJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            resolveActiveLocation()
            fetchFrames()
            startAutoRefresh()
        }
    }

    fun play() {
        if (_frames.value.isEmpty()) return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val frameCount = _frames.value.size
                if (frameCount == 0) break
                val next = (_currentFrameIndex.value + 1) % frameCount
                _currentFrameIndex.value = next
                // Pause at last frame before looping
                if (next == frameCount - 1) {
                    delay(1500)
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(index: Int) {
        val frameCount = _frames.value.size
        if (frameCount == 0) return
        pause()
        _currentFrameIndex.value = index.coerceIn(0, frameCount - 1)
    }

    fun seekToNow() {
        val now = System.currentTimeMillis()
        val lastPastIndex = _frames.value.indexOfLast { it.timestamp <= now }
        if (lastPastIndex >= 0) seekTo(lastPastIndex)
    }

    private suspend fun resolveActiveLocation() {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.activeLocationId > 0) {
            _activeLocation.value = locationRepository.getById(prefs.activeLocationId)
        }
    }

    private suspend fun fetchFrames() {
        _isLoading.value = true
        _error.value = null
        try {
            val response = rainViewerService.getFrameList()
            val mapped = OpenMeteoMapper.mapRadarFrames(response)
            _frames.value = mapped
            // Reset index if out of bounds
            if (_currentFrameIndex.value >= mapped.size) {
                _currentFrameIndex.value = 0
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load radar data"
        } finally {
            _isLoading.value = false
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10 * 60 * 1000L) // 10 minutes
                fetchFrames()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        autoRefreshJob?.cancel()
    }
}
