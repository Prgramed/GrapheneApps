package dev.emusic.domain.model

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val duration: Int = 0,
    val coverArtId: String? = null,
    val public: Boolean = false,
    val comment: String? = null,
    val createdAt: String? = null,
    val changedAt: String? = null,
    val tracks: List<Track> = emptyList(),
)
