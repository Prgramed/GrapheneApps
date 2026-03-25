package dev.emusic.data.radio

import dev.emusic.data.db.entity.CountryEntity
import dev.emusic.data.db.entity.RadioStationEntity
import dev.emusic.domain.model.RadioStation

fun RadioStationDto.toDomain(): RadioStation = RadioStation(
    stationUuid = stationuuid.orEmpty(),
    name = name.orEmpty(),
    url = url.orEmpty(),
    urlResolved = urlResolved,
    homepage = homepage,
    favicon = favicon?.ifBlank { null },
    country = country,
    countryCode = countryCode,
    language = language,
    codec = codec,
    bitrate = bitrate ?: 0,
    tags = tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    votes = votes ?: 0,
    isHls = isHls == 1,
)

fun RadioStationEntity.toDomain(): RadioStation = RadioStation(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
    homepage = homepage,
    favicon = favicon,
    country = country,
    countryCode = countryCode,
    language = language,
    codec = codec,
    bitrate = bitrate,
    tags = tags?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
    votes = votes,
    isHls = isHls,
    isFavourite = true,
)

fun RadioStation.toEntity(): RadioStationEntity = RadioStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
    homepage = homepage,
    favicon = favicon,
    country = country,
    countryCode = countryCode,
    language = language,
    codec = codec,
    bitrate = bitrate,
    tags = tags.joinToString(","),
    votes = votes,
    isHls = isHls,
)

fun CountryDto.toEntity(): CountryEntity = CountryEntity(
    code = code.orEmpty(),
    name = name.orEmpty(),
    stationCount = stationcount ?: 0,
)
