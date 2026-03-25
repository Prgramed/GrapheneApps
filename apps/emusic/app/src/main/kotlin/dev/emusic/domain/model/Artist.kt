package dev.emusic.domain.model

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
    val starred: Boolean = false,
)
