package dev.emusic.playback

import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

enum class ReplayGainMode(val label: String) {
    TRACK("Track Gain"),
    ALBUM("Album Gain"),
    AUTO("Auto"),
    OFF("Off"),
}

@Singleton
class ReplayGainManager @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val queueManager: QueueManager,
) {
    private var lastAlbumId: String? = null

    // Cached prefs — updated via flow, no more runBlocking
    @Volatile private var cachedMode: ReplayGainMode = ReplayGainMode.OFF
    @Volatile private var cachedPreAmpDb: Float = 0f

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            preferencesRepository.preferencesFlow
                .catch { }
                .collect { prefs ->
                    cachedMode = ReplayGainMode.entries.getOrElse(prefs.replayGainMode) { ReplayGainMode.OFF }
                    cachedPreAmpDb = prefs.preAmpDb
                }
        }
    }

    fun computeVolume(track: Track): Float {
        val mode = cachedMode
        val preAmpDb = cachedPreAmpDb

        if (mode == ReplayGainMode.OFF) {
            lastAlbumId = track.albumId
            return 1.0f
        }

        val (gainDb, peak) = selectGainAndPeak(track, mode)
        lastAlbumId = track.albumId

        if (gainDb == null) return 1.0f

        val totalGain = gainDb + preAmpDb
        var volume = 10f.pow(totalGain / 20f)

        // Clamp by peak to prevent clipping
        if (peak != null && peak > 0f) {
            volume = min(volume, 1.0f / peak)
        }

        return volume.coerceIn(0.01f, 3.0f)
    }

    private fun selectGainAndPeak(track: Track, mode: ReplayGainMode): Pair<Float?, Float?> {
        return when (mode) {
            ReplayGainMode.TRACK -> track.trackGain to track.trackPeak
            ReplayGainMode.ALBUM -> track.albumGain to track.albumPeak
            ReplayGainMode.AUTO -> {
                val useAlbum = lastAlbumId == track.albumId && track.albumGain != null
                if (useAlbum) {
                    track.albumGain to track.albumPeak
                } else {
                    track.trackGain to track.trackPeak
                }
            }
            ReplayGainMode.OFF -> null to null
        }
    }
}
