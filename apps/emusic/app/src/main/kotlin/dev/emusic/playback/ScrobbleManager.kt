package dev.emusic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.entity.ScrobbleEntity
import dev.emusic.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobbleManager @Inject constructor(
    private val apiService: SubsonicApiService,
    private val scrobbleDao: ScrobbleDao,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private var lastScrobbledTrackId: String? = null
    private var lastNowPlayingTrackId: String? = null

    fun startObserving(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastScrobbledTrackId = null
                mediaItem?.mediaId?.let { trackId ->
                    fireNowPlaying(trackId)
                }
            }
        })

        // Position ticker — check every 15s
        scope.launch {
            while (true) {
                delay(15_000)
                val playerState = withContext(Dispatchers.Main) {
                    if (!player.isPlaying) return@withContext null
                    Triple(
                        player.currentMediaItem?.mediaId ?: return@withContext null,
                        player.currentPosition,
                        player.duration.coerceAtLeast(0),
                    )
                } ?: continue

                val (trackId, positionMs, durationMs) = playerState
                if (positionMs >= 30_000 && trackId != lastScrobbledTrackId) {
                    fireScrobble(trackId, durationMs)
                }
            }
        }
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
        lastScrobbledTrackId = trackId

        scope.launch {
            if (!isScrobblingEnabled()) return@launch

            // API scrobble
            try {
                apiService.scrobble(trackId, submission = true)
            } catch (_: Exception) { }

            // Local record
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
