package dev.emusic.data.api

import dev.emusic.data.api.dto.AlbumDto
import dev.emusic.data.api.dto.AlbumInfoDto
import dev.emusic.data.api.dto.AlbumWithSongsDto
import dev.emusic.data.api.dto.ArtistDto
import dev.emusic.data.api.dto.ArtistInfoDto
import dev.emusic.data.api.dto.ArtistWithAlbumsDto
import dev.emusic.data.api.dto.LyricsDto
import dev.emusic.data.api.dto.PlaylistDto
import dev.emusic.data.api.dto.PlaylistWithSongsDto
import dev.emusic.data.api.dto.TrackDto
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.AlbumInfo
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.ArtistInfo
import dev.emusic.domain.model.Lyrics
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.model.SyncedLine
import dev.emusic.domain.model.Track

fun TrackDto.toDomain(): Track = Track(
    id = id.orEmpty(),
    title = title.orEmpty(),
    artist = artist.orEmpty(),
    artistId = artistId.orEmpty(),
    album = album.orEmpty(),
    albumId = albumId.orEmpty(),
    coverArtId = coverArt,
    duration = duration ?: 0,
    trackNumber = track ?: 0,
    discNumber = discNumber ?: 1,
    year = year,
    genre = genre,
    size = size ?: 0,
    contentType = contentType,
    suffix = suffix,
    bitRate = bitRate,
    starred = starred != null,
    playCount = playCount ?: 0,
    userRating = userRating,
    trackGain = replayGain?.trackGain,
    albumGain = replayGain?.albumGain,
    trackPeak = replayGain?.trackPeak,
    albumPeak = replayGain?.albumPeak,
)

fun AlbumDto.toDomain(): Album = Album(
    id = id.orEmpty(),
    name = name.orEmpty(),
    artist = artist.orEmpty(),
    artistId = artistId.orEmpty(),
    coverArtId = coverArt,
    trackCount = songCount ?: 0,
    duration = duration ?: 0,
    year = year,
    genre = genre,
    starred = starred != null,
    playCount = playCount ?: 0,
)

fun AlbumWithSongsDto.toAlbum(): Album = Album(
    id = id.orEmpty(),
    name = name.orEmpty(),
    artist = artist.orEmpty(),
    artistId = artistId.orEmpty(),
    coverArtId = coverArt,
    trackCount = songCount ?: 0,
    duration = duration ?: 0,
    year = year,
    genre = genre,
    starred = starred != null,
    playCount = playCount ?: 0,
)

fun ArtistDto.toDomain(): Artist = Artist(
    id = id.orEmpty(),
    name = name.orEmpty(),
    albumCount = albumCount ?: 0,
    coverArtId = coverArt,
    starred = starred != null,
)

fun ArtistWithAlbumsDto.toArtist(): Artist = Artist(
    id = id.orEmpty(),
    name = name.orEmpty(),
    albumCount = albumCount ?: 0,
    coverArtId = coverArt,
    starred = starred != null,
)

fun PlaylistDto.toDomain(): Playlist = Playlist(
    id = id.orEmpty(),
    name = name.orEmpty(),
    trackCount = songCount ?: 0,
    duration = duration ?: 0,
    coverArtId = coverArt,
    public = public ?: false,
    comment = comment,
    createdAt = created,
    changedAt = changed,
)

fun PlaylistWithSongsDto.toDomain(): Playlist = Playlist(
    id = id.orEmpty(),
    name = name.orEmpty(),
    trackCount = songCount ?: 0,
    duration = duration ?: 0,
    coverArtId = coverArt,
    public = public ?: false,
    comment = comment,
    createdAt = created,
    changedAt = changed,
    tracks = entry.map { it.toDomain() },
)

fun ArtistInfoDto.toDomain(): ArtistInfo = ArtistInfo(
    biography = biography,
    musicBrainzId = musicBrainzId,
    lastFmUrl = lastFmUrl,
    smallImageUrl = smallImageUrl,
    mediumImageUrl = mediumImageUrl,
    largeImageUrl = largeImageUrl,
    similarArtists = similarArtist.map { it.toDomain() },
)

fun AlbumInfoDto.toDomain(): AlbumInfo = AlbumInfo(
    notes = notes,
    musicBrainzId = musicBrainzId,
    lastFmUrl = lastFmUrl,
    smallImageUrl = smallImageUrl,
    mediumImageUrl = mediumImageUrl,
    largeImageUrl = largeImageUrl,
)

fun LyricsDto.toDomain(): Lyrics {
    val text = value.orEmpty()
    val syncedLines = parseLrc(text)
    return Lyrics(
        artist = artist,
        title = title,
        text = if (syncedLines.isEmpty()) text else null,
        syncedLines = syncedLines,
    )
}

private val LRC_LINE_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

private fun parseLrc(text: String): List<SyncedLine> {
    return text.lines().mapNotNull { line ->
        LRC_LINE_REGEX.matchEntire(line.trim())?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@let null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@let null
            val milliStr = match.groupValues[3]
            val millis = when (milliStr.length) {
                2 -> (milliStr.toLongOrNull() ?: 0) * 10
                3 -> milliStr.toLongOrNull() ?: 0
                else -> 0
            }
            val timeMs = minutes * 60_000 + seconds * 1_000 + millis
            SyncedLine(timeMs = timeMs, text = match.groupValues[4].trim())
        }
    }
}
