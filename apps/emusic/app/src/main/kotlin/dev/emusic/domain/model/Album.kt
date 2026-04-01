package dev.emusic.domain.model

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArtId: String? = null,
    val trackCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: Boolean = false,
    val playCount: Int = 0,
    val pinned: Boolean = false,
)
