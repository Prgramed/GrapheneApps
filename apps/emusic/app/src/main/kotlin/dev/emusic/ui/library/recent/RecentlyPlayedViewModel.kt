package dev.emusic.ui.library.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecentMode { ALBUMS, ARTISTS, TRACKS }

data class RecentAlbumItem(val album: Album, val lastPlayedMs: Long)
data class RecentArtistItem(val artist: Artist, val lastPlayedMs: Long)
data class RecentTrackItem(val track: Track, val playedAtMs: Long)

@HiltViewModel
class RecentlyPlayedViewModel @Inject constructor(
    private val scrobbleDao: ScrobbleDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(RecentMode.ALBUMS)
    val mode: StateFlow<RecentMode> = _mode.asStateFlow()

    private val _recentAlbums = MutableStateFlow<List<RecentAlbumItem>>(emptyList())
    val recentAlbums: StateFlow<List<RecentAlbumItem>> = _recentAlbums.asStateFlow()

    private val _recentArtists = MutableStateFlow<List<RecentArtistItem>>(emptyList())
    val recentArtists: StateFlow<List<RecentArtistItem>> = _recentArtists.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<RecentTrackItem>>(emptyList())
    val recentTracks: StateFlow<List<RecentTrackItem>> = _recentTracks.asStateFlow()

    init {
        viewModelScope.launch {
            scrobbleDao.getRecentAlbumsFlow(100).collect { results ->
                _recentAlbums.value = results.mapNotNull { r ->
                    albumDao.getById(r.albumId)?.toDomain()?.let { album ->
                        RecentAlbumItem(album, r.lastPlayed)
                    }
                }
            }
        }
        viewModelScope.launch {
            scrobbleDao.getRecentArtistsFlow(100).collect { results ->
                _recentArtists.value = results.mapNotNull { r ->
                    artistDao.getById(r.artistId)?.toDomain()?.let { artist ->
                        RecentArtistItem(artist, r.lastPlayed)
                    }
                }
            }
        }
        viewModelScope.launch {
            scrobbleDao.getAllScrobblesDesc(200).collect { scrobbles ->
                _recentTracks.value = scrobbles.mapNotNull { s ->
                    trackDao.getById(s.trackId)?.toDomain()?.let { track ->
                        RecentTrackItem(track, s.timestamp)
                    }
                }
            }
        }
    }

    fun setMode(mode: RecentMode) { _mode.value = mode }

    fun getCoverArtUrl(id: String): String = libraryRepository.getCoverArtUrl(id, 100)

    fun clearHistory() {
        viewModelScope.launch { scrobbleDao.deleteAll() }
    }
}
