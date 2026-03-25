package dev.emusic.playback

import androidx.media3.common.Metadata
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.extractor.metadata.icy.IcyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class IcyNowPlaying(
    val artist: String?,
    val title: String?,
    val raw: String,
)

@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class IcyMetadataHandler @Inject constructor() : MetadataOutput {

    private val _nowPlaying = MutableStateFlow<IcyNowPlaying?>(null)
    val nowPlaying: StateFlow<IcyNowPlaying?> = _nowPlaying.asStateFlow()

    override fun onMetadata(metadata: Metadata) {
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is IcyInfo) {
                val streamTitle = entry.title ?: continue
                val parsed = parseStreamTitle(streamTitle)
                _nowPlaying.value = parsed
            }
        }
    }

    fun clear() {
        _nowPlaying.value = null
    }

    private fun parseStreamTitle(raw: String): IcyNowPlaying {
        val parts = raw.split(" - ", limit = 2)
        return if (parts.size == 2) {
            IcyNowPlaying(
                artist = parts[0].trim().ifEmpty { null },
                title = parts[1].trim().ifEmpty { null },
                raw = raw,
            )
        } else {
            IcyNowPlaying(artist = null, title = raw.trim(), raw = raw)
        }
    }
}
