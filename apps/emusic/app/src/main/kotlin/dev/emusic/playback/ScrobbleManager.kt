package dev.emusic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.entity.ScrobbleEntity
import dev.emusic.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobbleManager @Inject constructor(
    private val apiService: SubsonicApiService,
    private val scrobbleDao: ScrobbleDao,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private var lastScrobbledTrackId: String? = null
    private var lastNowPlayingTrackId: String? = null
    private var scrobbleJob: Job? = null

    fun startObserving(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastScrobbledTrackId = null
                scrobbleJob?.cancel()
                mediaItem?.mediaId?.let { trackId ->
                    fireNowPlaying(trackId)
                    // Schedule scrobble after 30 seconds of playback
                    scrobbleJob = scope.launch {
                        delay(30_000)
                        if (player.isPlaying && player.currentMediaItem?.mediaId == trackId) {
                            fireScrobble(trackId, player.duration.coerceAtLeast(0))
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    scrobbleJob?.cancel()
                } else {
                    // Resume scrobble timer if not yet scrobbled
                    val trackId = player.currentMediaItem?.mediaId ?: return
                    if (trackId != lastScrobbledTrackId && player.currentPosition >= 30_000) {
                        fireScrobble(trackId, player.duration.coerceAtLeast(0))
                    } else if (trackId != lastScrobbledTrackId) {
                        val remaining = (30_000 - player.currentPosition).coerceAtLeast(1000)
                        scrobbleJob?.cancel()
                        scrobbleJob = scope.launch {
                            delay(remaining)
                            if (player.isPlaying && player.currentMediaItem?.mediaId == trackId) {
                                fireScrobble(trackId, player.duration.coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        })
    }

    private fun fireNowPlaying(trackId: String) {
        if (trackId == lastNowPlayingTrackId) return
        lastNowPlayingTrackId = trackId

        scope.launch {
            if (!isScrobblingEnabled()) return@launch
            try {
                apiService.scrobble(trackId, submission = false)
            } catch (_: Exception) { }
        }
    }

    private fun fireScrobble(trackId: String, durationMs: Long) {
        if (trackId == lastScrobbledTrackId) return
        lastScrobbledTrackId = trackId

        scope.launch {
            if (!isScrobblingEnabled()) return@launch

            try {
                apiService.scrobble(trackId, submission = true)
            } catch (_: Exception) { }

            scrobbleDao.insert(
                ScrobbleEntity(
                    trackId = trackId,
                    timestamp = System.currentTimeMillis(),
                    durationMs = durationMs,
                ),
            )
        }
    }

    private suspend fun isScrobblingEnabled(): Boolean =
        preferencesRepository.preferencesFlow.first().scrobblingEnabled
}
