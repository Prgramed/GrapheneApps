package dev.emusic.domain.model

data class Lyrics(
    val artist: String? = null,
    val title: String? = null,
    val text: String? = null,
    val syncedLines: List<SyncedLine> = emptyList(),
) {
    val isSynced: Boolean get() = syncedLines.isNotEmpty()
}

data class SyncedLine(
    val timeMs: Long,
    val text: String,
)
