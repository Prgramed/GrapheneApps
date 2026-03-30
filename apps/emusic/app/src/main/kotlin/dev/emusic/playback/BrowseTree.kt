package dev.emusic.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.emusic.data.api.SubsonicUrlBuilder
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.TrackDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseTree @Inject constructor(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val urlBuilder: SubsonicUrlBuilder,
) {
    companion object {
        const val ROOT = "root"
        const val ALBUMS = "browse_albums"
        const val ARTISTS = "browse_artists"
        const val PLAYLISTS = "browse_playlists"
        const val RECENT = "browse_recent"
        const val ALBUM_PREFIX = "album_"
        const val ARTIST_PREFIX = "artist_"
        const val PLAYLIST_PREFIX = "playlist_"
    }

    fun getRootItems(): List<MediaItem> = listOf(
        browsableItem(ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        browsableItem(ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        browsableItem(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        browsableItem(RECENT, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
    )

    suspend fun getChildren(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val offset = page * pageSize
        return when {
            parentId == ALBUMS -> {
                albumDao.getNewest(500).drop(offset).take(pageSize).map { album ->
                    browsableItem(
                        ALBUM_PREFIX + album.id,
                        album.name,
                        MediaMetadata.MEDIA_TYPE_ALBUM,
                        subtitle = album.artist,
                        artworkId = album.coverArtId,
                    )
                }
            }
            parentId == ARTISTS -> {
                artistDao.getAllSorted().drop(offset).take(pageSize).map { artist ->
                    browsableItem(
                        ARTIST_PREFIX + artist.id,
                        artist.name,
                        MediaMetadata.MEDIA_TYPE_ARTIST,
                        artworkId = artist.coverArtId,
                    )
                }
            }
            parentId == PLAYLISTS -> {
                playlistDao.getAllSorted().drop(offset).take(pageSize).map { playlist ->
                    browsableItem(
                        PLAYLIST_PREFIX + playlist.id,
                        playlist.name,
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                        artworkId = playlist.coverArtId,
                    )
                }
            }
            parentId == RECENT -> {
                trackDao.getTopByPlayCount(50).drop(offset).take(pageSize).map { track ->
                    playableItem(track.id, track.title, track.artist, track.album, track.albumId)
                }
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val albumId = parentId.removePrefix(ALBUM_PREFIX)
                trackDao.getByAlbumId(albumId).drop(offset).take(pageSize).map { track ->
                    playableItem(track.id, track.title, track.artist, track.album, track.albumId)
                }
            }
            parentId.startsWith(ARTIST_PREFIX) -> {
                val artistId = parentId.removePrefix(ARTIST_PREFIX)
                albumDao.getByArtistId(artistId).drop(offset).take(pageSize).map { album ->
                    browsableItem(
                        ALBUM_PREFIX + album.id,
                        album.name,
                        MediaMetadata.MEDIA_TYPE_ALBUM,
                        subtitle = album.artist,
                        artworkId = album.coverArtId,
                    )
                }
            }
            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                val tracks = playlistDao.observePlaylistTracks(playlistId).first()
                tracks.drop(offset).take(pageSize).map { entity ->
                    playableItem(entity.id, entity.title, entity.artist, entity.album, entity.albumId)
                }
            }
            else -> emptyList()
        }
    }

    private fun browsableItem(
        id: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
        artworkId: String? = null,
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        artworkId?.let { meta.setArtworkUri(Uri.parse(urlBuilder.getCoverArtUrlWithAuth(it))) }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(meta.build())
            .build()
    }

    private fun playableItem(
        id: String,
        title: String,
        artist: String,
        album: String,
        albumId: String? = null,
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        albumId?.let { meta.setArtworkUri(Uri.parse(urlBuilder.getCoverArtUrlWithAuth(it))) }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(meta.build())
            .build()
    }
}
