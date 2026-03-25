package dev.emusic.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    val remainingMs: Long = 0,
    val isActive: Boolean = false,
    val stopAfterTrack: Boolean = false,
    val fired: Boolean = false,
)

@Singleton
class SleepTimerManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var timerJob: Job? = null
    private var player: ExoPlayer? = null
    private var targetTimeMs: Long = 0

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun setPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
    }

    fun set(minutes: Int) {
        targetTimeMs = System.currentTimeMillis() + minutes * 60_000L
        _state.value = SleepTimerState(
            remainingMs = minutes * 60_000L,
            isActive = true,
        )
        startTicker()
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        targetTimeMs = 0
        _state.value = SleepTimerState()
    }

    fun extend(minutes: Int) {
        if (!_state.value.isActive) return
        targetTimeMs += minutes * 60_000L
        _state.value = _state.value.copy(
            remainingMs = (targetTimeMs - System.currentTimeMillis()).coerceAtLeast(0),
        )
    }

    fun setStopAfterTrack(enabled: Boolean) {
        _state.value = _state.value.copy(stopAfterTrack = enabled)
    }

    fun checkStopAfterTrack(): Boolean {
        if (_state.value.stopAfterTrack) {
            player?.pause()
            cancel()
            return true
        }
        return false
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val remaining = targetTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    // Timer expired
                    player?.pause()
                    player?.volume = 1f
                    _state.value = SleepTimerState(fired = true)
                    timerJob = null
                    break
                }

                // Fade volume in last 60 seconds
                if (remaining <= 60_000) {
                    val fadeVolume = (remaining / 60_000f).coerceIn(0f, 1f)
                    player?.volume = fadeVolume
                }

                _state.value = _state.value.copy(
                    remainingMs = remaining,
                    isActive = true,
                )

                delay(if (remaining <= 60_000) 250 else 1000)
            }
        }
    }
}
