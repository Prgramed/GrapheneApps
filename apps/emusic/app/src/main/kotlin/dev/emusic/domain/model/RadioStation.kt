package dev.emusic.domain.model

data class RadioStation(
    val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String? = null,
    val homepage: String? = null,
    val favicon: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val tags: List<String> = emptyList(),
    val votes: Int = 0,
    val isHls: Boolean = false,
    val isFavourite: Boolean = false,
)
