package dev.emusic.data.db.entity

import dev.emusic.domain.model.Album
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.model.Track

// --- Track ---

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    duration = duration,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    size = size,
    contentType = contentType,
    suffix = suffix,
    bitRate = bitRate,
    starred = starred,
    playCount = playCount,
    userRating = userRating,
    localPath = localPath,
    trackGain = trackGain,
    albumGain = albumGain,
    trackPeak = trackPeak,
    albumPeak = albumPeak,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    duration = duration,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    size = size,
    contentType = contentType,
    suffix = suffix,
    bitRate = bitRate,
    starred = starred,
    playCount = playCount,
    userRating = userRating,
    localPath = localPath,
    trackGain = trackGain,
    albumGain = albumGain,
    trackPeak = trackPeak,
    albumPeak = albumPeak,
)

// --- Album ---

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    trackCount = trackCount,
    duration = duration,
    year = year,
    genre = genre,
    starred = starred,
    playCount = playCount,
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    trackCount = trackCount,
    duration = duration,
    year = year,
    genre = genre,
    starred = starred,
    playCount = playCount,
)

// --- Artist ---

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    coverArtId = coverArtId,
    starred = starred,
)

fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    albumCount = albumCount,
    coverArtId = coverArtId,
    starred = starred,
)

// --- Playlist ---

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    duration = duration,
    coverArtId = coverArtId,
    public = public,
    comment = comment,
    createdAt = createdAt,
    changedAt = changedAt,
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    trackCount = trackCount,
    duration = duration,
    coverArtId = coverArtId,
    public = public,
    comment = comment,
    createdAt = createdAt,
    changedAt = changedAt,
)
