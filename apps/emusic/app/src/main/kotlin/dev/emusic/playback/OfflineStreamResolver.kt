package dev.emusic.playback

import android.net.Uri
import android.util.Log
import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineStreamResolver @Inject constructor(
    private val urlBuilder: SubsonicUrlBuilder,
    preferencesRepository: AppPreferencesRepository,
) {
    @Volatile
    private var forceOffline: Boolean = false

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            preferencesRepository.preferencesFlow
                .map { it.forceOfflineMode }
                .catch { }
                .collect { forceOffline = it }
        }
    }

    fun resolveUri(track: Track, maxBitRate: Int = 0): Uri {
        // Always prefer local file
        val localPath = track.localPath
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Playing offline: ${track.title} from $localPath")
                return Uri.fromFile(file)
            } else {
                Log.w(TAG, "Local file missing or empty for ${track.title}: $localPath")
            }
        }

        // In forced offline mode, don't attempt streaming
        if (forceOffline) {
            Log.w(TAG, "Offline mode: skipping non-downloaded track: ${track.title}")
            return Uri.EMPTY
        }

        Log.d(TAG, "Streaming: ${track.title}")
        return Uri.parse(urlBuilder.getStreamUrl(track.id, maxBitRate))
    }

    companion object {
        private const val TAG = "OfflineStreamResolver"
    }
}
