package dev.emusic.data.radio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioStationDto(
    val stationuuid: String? = null,
    val name: String? = null,
    val url: String? = null,
    @SerialName("url_resolved") val urlResolved: String? = null,
    val homepage: String? = null,
    val favicon: String? = null,
    val country: String? = null,
    @SerialName("countrycode") val countryCode: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val tags: String? = null,
    val votes: Int? = null,
    @SerialName("hls") val isHls: Int? = null,
    @SerialName("lastcheckok") val lastCheckedOk: Int? = null,
)

@Serializable
data class CountryDto(
    val name: String? = null,
    @SerialName("iso_3166_1") val code: String? = null,
    val stationcount: Int? = null,
)
