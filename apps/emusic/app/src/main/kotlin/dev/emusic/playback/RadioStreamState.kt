package dev.emusic.playback

import dev.emusic.domain.model.RadioStation

sealed interface RadioStreamState {
    data object Idle : RadioStreamState
    data class Loading(val station: RadioStation) : RadioStreamState
    data class Playing(val station: RadioStation, val icyNowPlaying: IcyNowPlaying?) : RadioStreamState
    data class Reconnecting(val station: RadioStation, val attempt: Int) : RadioStreamState
    data class Offline(val station: RadioStation) : RadioStreamState
}
