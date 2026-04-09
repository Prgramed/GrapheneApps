package dev.emusic.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.LyricsDao
import dev.emusic.data.db.entity.LyricsEntity
import dev.emusic.domain.model.Lyrics
import dev.emusic.domain.model.SyncedLine
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.IcyMetadataHandler
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioNowPlayingBridge
import dev.emusic.playback.RadioStreamError
import dev.emusic.playback.SleepTimerManager
import dev.emusic.playback.SleepTimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class NowPlayingUiState(
    val track: Track? = null,
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val lyrics: Lyrics? = null,
    val showLyrics: Boolean = false,
    val isLiveStream: Boolean = false,
    val radioStationName: String? = null,
    val radioFavicon: String? = null,
    val icyArtist: String? = null,
    val icyTitle: String? = null,
    val sleepTimerState: SleepTimerState = SleepTimerState(),
    val radioStreamError: RadioStreamError? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    application: Application,
    private val queueManager: QueueManager,
    val libraryRepository: LibraryRepository,
    private val lyricsDao: LyricsDao,
    private val radioNowPlayingBridge: RadioNowPlayingBridge,
    private val icyMetadataHandler: IcyMetadataHandler,
    val castManager: dev.emusic.playback.cast.CastManager,
    private val sleepTimerManager: SleepTimerManager,
    sessionToken: SessionToken,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

    init {
        controllerFuture.addListener({
            val mc = controllerFuture.get()
            controller = mc
            syncStateFromPlayer(mc)
            mc.addListener(playerListener)
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            queueManager.currentTrack.collect { track ->
                _uiState.update {
                    it.copy(
                        track = track,
                        coverArtUrl = track?.let { t ->
                            libraryRepository.getCoverArtUrl(t.coverArtId ?: t.albumId)
                        },
                        lyrics = null, // Reset on track change
                    )
                }
                // Load lyrics
                if (track != null) {
                    loadLyrics(track)
                }
            }
        }

        viewModelScope.launch {
            combine(
                queueManager.isLiveStream,
                radioNowPlayingBridge.currentStation,
                icyMetadataHandler.nowPlaying,
            ) { isLive, station, icy ->
                Triple(isLive, station, icy)
            }.collect { (isLive, station, icy) ->
                _uiState.update {
                    it.copy(
                        isLiveStream = isLive,
                        radioStationName = station?.name,
                        radioFavicon = station?.favicon,
                        icyArtist = icy?.artist,
                        icyTitle = icy?.title,
                    )
                }
            }
        }

        viewModelScope.launch {
            sleepTimerManager.state.collect { timerState ->
                _uiState.update { it.copy(sleepTimerState = timerState) }
            }
        }

        viewModelScope.launch {
            radioNowPlayingBridge.streamError.collect { error ->
                _uiState.update { it.copy(radioStreamError = error) }
            }
        }

        // Cast state tracking
        viewModelScope.launch {
            castManager.activeDevice.collect { device ->
                _uiState.update {
                    it.copy(
                        isCasting = device != null,
                        castDeviceName = device?.name,
                    )
                }
            }
        }
        // Sync play/pause UI with cast state — ignore transient CONNECTING state
        viewModelScope.launch {
            castManager.castState.collect { state ->
                if (castManager.isCasting && state != dev.emusic.playback.cast.CastState.CONNECTING) {
                    _uiState.update {
                        it.copy(isPlaying = state == dev.emusic.playback.cast.CastState.PLAYING)
                    }
                }
            }
        }

        // Position polling — only active when playing or casting
        viewModelScope.launch {
            while (true) {
                val mc = controller
                if (mc != null && (mc.isPlaying || castManager.isCasting)) {
                    _uiState.update {
                        it.copy(
                            positionMs = mc.currentPosition,
                            durationMs = mc.duration.coerceAtLeast(0),
                        )
                    }
                    delay(1000)
                } else {
                    delay(3000) // Slow poll when paused
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // When casting, UI play state follows cast state, not local player
            if (castManager.isCasting) return
            val mc = controller ?: return
            _uiState.update {
                it.copy(
                    isPlaying = isPlaying,
                    positionMs = mc.currentPosition,
                    durationMs = mc.duration.coerceAtLeast(0),
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { mc ->
                syncStateFromPlayer(mc)
            }
            // Force-read current track so UI updates immediately on skip
            val track = queueManager.currentTrack.value
            if (track != null && track.id != _uiState.value.track?.id) {
                _uiState.update {
                    it.copy(
                        track = track,
                        coverArtUrl = libraryRepository.getCoverArtUrl(track.coverArtId ?: track.albumId),
                        lyrics = null,
                    )
                }
                viewModelScope.launch { loadLyrics(track) }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }
    }

    private fun syncStateFromPlayer(mc: MediaController) {
        _uiState.update {
            it.copy(
                isPlaying = mc.isPlaying,
                positionMs = mc.currentPosition,
                durationMs = mc.duration.coerceAtLeast(0),
                shuffleEnabled = mc.shuffleModeEnabled,
                repeatMode = mc.repeatMode,
            )
        }
    }

    fun retryRadio() {
        val station = radioNowPlayingBridge.currentStation.value ?: return
        val mc = controller ?: return
        radioNowPlayingBridge.onStreamRecovered()
        mc.prepare()
        mc.play()
    }

    fun setSleepTimer(minutes: Int) { sleepTimerManager.set(minutes) }
    fun extendSleepTimer(minutes: Int) { sleepTimerManager.extend(minutes) }
    fun cancelSleepTimer() { sleepTimerManager.cancel() }
    fun toggleStopAfterTrack() { sleepTimerManager.setStopAfterTrack(!_uiState.value.sleepTimerState.stopAfterTrack) }

    fun toggleStar() {
        val track = _uiState.value.track ?: return
        val newStarred = !track.starred
        _uiState.update { it.copy(track = it.track?.copy(starred = newStarred)) }
        viewModelScope.launch {
            try {
                if (newStarred) libraryRepository.starTrack(track.id)
                else libraryRepository.unstarTrack(track.id)
            } catch (_: Exception) {
                // Rollback on failure
                _uiState.update { it.copy(track = it.track?.copy(starred = !newStarred)) }
            }
        }
    }

    fun rateTrack(rating: Int) {
        val track = _uiState.value.track ?: return
        _uiState.update { it.copy(track = it.track?.copy(userRating = rating)) }
        viewModelScope.launch {
            try {
                libraryRepository.setRating(track.id, rating)
            } catch (_: Exception) { }
        }
    }

    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
    }

    private val lrcLineRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    private suspend fun loadLyrics(track: Track) {
        val ttl = 30L * 24 * 60 * 60 * 1000 // 30 days
        val cached = lyricsDao.getByTrackId(track.id)

        // Use cache if valid
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < ttl) {
            if (cached.lrcContent != null) {
                // Parse cached LRC content directly — no API call
                val syncedLines = cached.lrcContent.lines().mapNotNull { line ->
                    lrcLineRegex.matchEntire(line.trim())?.let { match ->
                        val mins = match.groupValues[1].toLongOrNull() ?: return@let null
                        val secs = match.groupValues[2].toLongOrNull() ?: return@let null
                        val milliStr = match.groupValues[3]
                        val millis = when (milliStr.length) {
                            2 -> (milliStr.toLongOrNull() ?: 0) * 10
                            3 -> milliStr.toLongOrNull() ?: 0
                            else -> 0
                        }
                        SyncedLine(timeMs = mins * 60_000 + secs * 1_000 + millis, text = match.groupValues[4].trim())
                    }
                }
                _uiState.update { it.copy(lyrics = Lyrics(syncedLines = syncedLines)) }
                return
            } else if (cached.plainText != null) {
                _uiState.update { it.copy(lyrics = Lyrics(text = cached.plainText)) }
                return
            }
        }

        // Cache miss or expired — fetch from API
        try {
            val lyrics = libraryRepository.getLyrics(track.artist, track.title)
            _uiState.update { it.copy(lyrics = lyrics) }
            if (lyrics != null) {
                lyricsDao.upsert(
                    LyricsEntity(
                        trackId = track.id,
                        lrcContent = if (lyrics.isSynced) lyrics.syncedLines.joinToString("\n") { "[${formatLrcTime(it.timeMs)}]${it.text}" } else null,
                        plainText = lyrics.text,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (_: Exception) {
            // No lyrics available
        }
    }

    private fun formatLrcTime(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val hundredths = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }

    fun setCastVolume(percent: Int) {
        castManager.setVolume(percent)
    }

    fun playPause() {
        if (castManager.isCasting) {
            val state = castManager.castState.value
            if (state == dev.emusic.playback.cast.CastState.PLAYING) {
                castManager.pause()
                controller?.pause() // Keep local player in sync (muted)
            } else {
                castManager.play()
                controller?.play() // Keep local player in sync (muted)
            }
        } else {
            controller?.let { mc ->
                if (mc.isPlaying) {
                    mc.pause()
                } else {
                    // If player is idle (e.g. after cold restart), re-prepare before playing
                    if (mc.playbackState == Player.STATE_IDLE || mc.playbackState == Player.STATE_ENDED) {
                        mc.prepare()
                    }
                    mc.play()
                }
            }
        }
    }

    fun skipNext() {
        // Skip always goes through local player — track change listener forwards to cast
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        if (castManager.isCasting) {
            castManager.seek(positionMs)
            controller?.seekTo(positionMs) // Keep local in sync
        } else {
            controller?.seekTo(positionMs)
        }
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() {
        controller?.let { mc ->
            val newShuffle = !mc.shuffleModeEnabled
            mc.shuffleModeEnabled = newShuffle
            // Immediately update UI (don't wait for listener callback)
            _uiState.update { it.copy(shuffleEnabled = newShuffle) }
            Timber.d("Shuffle toggled: $newShuffle")
        } ?: Timber.w("toggleShuffle: controller not ready")
    }

    fun cycleRepeatMode() {
        controller?.let { mc ->
            val newMode = when (mc.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            mc.repeatMode = newMode
            // Immediately update UI
            _uiState.update { it.copy(repeatMode = newMode) }
            Timber.d("Repeat mode: $newMode")
        } ?: Timber.w("cycleRepeatMode: controller not ready")
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
