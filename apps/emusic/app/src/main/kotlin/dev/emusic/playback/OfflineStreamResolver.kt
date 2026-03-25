package dev.emusic.playback

import android.net.Uri
import android.util.Log
import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.domain.model.Track
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineStreamResolver @Inject constructor(
    private val urlBuilder: SubsonicUrlBuilder,
) {
    fun resolveUri(track: Track, maxBitRate: Int = 0): Uri {
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
        Log.d(TAG, "Streaming: ${track.title}")
        return Uri.parse(urlBuilder.getStreamUrl(track.id, maxBitRate))
    }

    companion object {
        private const val TAG = "OfflineStreamResolver"
    }
}
