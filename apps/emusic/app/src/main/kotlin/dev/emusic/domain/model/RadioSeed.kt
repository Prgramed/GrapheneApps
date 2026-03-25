package dev.emusic.domain.model

sealed interface RadioSeed {
    data class FromTrack(val trackId: String) : RadioSeed
    data class FromArtist(val artistId: String, val artistName: String) : RadioSeed
}
