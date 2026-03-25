package dev.emusic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val trackDao: TrackDao,
    private val scrobbleDao: ScrobbleDao,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _smartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = _smartPlaylists.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect {
                _playlists.value = it
            }
        }
        viewModelScope.launch {
            combine(
                trackDao.observeStarredCount(),
                trackDao.observeNeverPlayedCount(),
                scrobbleDao.getTopTrackIdsAllTime(50),
                scrobbleDao.getRecentDistinctTrackIds(50),
            ) { starredCount, neverPlayedCount, topTrackIds, recentTrackIds ->
                buildList {
                    if (starredCount > 0) add(SmartPlaylist("smart_starred", "Starred Tracks", starredCount))
                    if (topTrackIds.isNotEmpty()) add(SmartPlaylist("smart_most_played", "Most Played", topTrackIds.size))
                    if (recentTrackIds.isNotEmpty()) add(SmartPlaylist("smart_recent", "Recent 50", recentTrackIds.size))
                    if (neverPlayedCount > 0) add(SmartPlaylist("smart_never_played", "Never Played", neverPlayedCount))
                }
            }.collect { _smartPlaylists.value = it }
        }
    }

    fun getCoverArtUrl(id: String): String =
        libraryRepository.getCoverArtUrl(id)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try { playlistRepository.createPlaylist(name) } catch (_: Exception) { }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch {
            try { playlistRepository.renamePlaylist(id, name) } catch (_: Exception) { }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            try { playlistRepository.deletePlaylist(id) } catch (_: Exception) { }
        }
    }
}
