package dev.emusic.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseWrapper(
    @SerialName("subsonic-response")
    val subsonicResponse: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String? = null,
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: ErrorDto? = null,
    val artists: ArtistsDto? = null,
    val artist: ArtistWithAlbumsDto? = null,
    val album: AlbumWithSongsDto? = null,
    val albumList2: AlbumListDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistWithSongsDto? = null,
    val similarSongs2: SimilarSongsDto? = null,
    val topSongs: TopSongsDto? = null,
    val randomSongs: RandomSongsDto? = null,
    val starred2: StarredDto? = null,
    val lyrics: LyricsDto? = null,
    val artistInfo2: ArtistInfoDto? = null,
    val albumInfo: AlbumInfoDto? = null,
    val playQueue: PlayQueueDto? = null,
) {
    val isOk: Boolean get() = status == "ok"
}

// --- Error ---

@Serializable
data class ErrorDto(
    val code: Int? = null,
    val message: String? = null,
)

// --- Artists ---

@Serializable
data class ArtistsDto(
    val ignoredArticles: String? = null,
    val index: List<IndexDto> = emptyList(),
)

@Serializable
data class IndexDto(
    val name: String? = null,
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class ArtistDto(
    val id: String? = null,
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
)

@Serializable
data class ArtistWithAlbumsDto(
    val id: String? = null,
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
    val album: List<AlbumDto> = emptyList(),
)

// --- Albums ---

@Serializable
data class AlbumListDto(
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumDto(
    val id: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val playCount: Int? = null,
)

@Serializable
data class AlbumWithSongsDto(
    val id: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val playCount: Int? = null,
    val song: List<TrackDto> = emptyList(),
)

// --- Tracks (child/song) ---

@Serializable
data class TrackDto(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
    val playCount: Int? = null,
    val userRating: Int? = null,
    val replayGain: ReplayGainDto? = null,
)

@Serializable
data class ReplayGainDto(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
)

// --- Playlists ---

@Serializable
data class PlaylistsDto(
    val playlist: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistDto(
    val id: String? = null,
    val name: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val public: Boolean? = null,
    val comment: String? = null,
    val created: String? = null,
    val changed: String? = null,
)

@Serializable
data class PlaylistWithSongsDto(
    val id: String? = null,
    val name: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val public: Boolean? = null,
    val comment: String? = null,
    val created: String? = null,
    val changed: String? = null,
    val entry: List<TrackDto> = emptyList(),
)

// --- Search ---

@Serializable
data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<TrackDto> = emptyList(),
)

// --- Similar / Top / Random ---

@Serializable
data class SimilarSongsDto(
    val song: List<TrackDto> = emptyList(),
)

@Serializable
data class TopSongsDto(
    val song: List<TrackDto> = emptyList(),
)

@Serializable
data class RandomSongsDto(
    val song: List<TrackDto> = emptyList(),
)

// --- Starred ---

@Serializable
data class StarredDto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<TrackDto> = emptyList(),
)

// --- Lyrics ---

@Serializable
data class LyricsDto(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null,
)

// --- Artist Info ---

@Serializable
data class ArtistInfoDto(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<ArtistDto> = emptyList(),
)

// --- Album Info ---

@Serializable
data class AlbumInfoDto(
    val notes: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
)

// --- Play Queue ---

@Serializable
data class PlayQueueDto(
    val entry: List<TrackDto> = emptyList(),
    val current: String? = null,
    val position: Long? = null,
    val changed: String? = null,
    val changedBy: String? = null,
)
