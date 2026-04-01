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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
)

enum class PlaylistSort(val label: String) {
    NAME("Name"),
    TRACK_COUNT("Track Count"),
    NEWEST("Newest"),
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val trackDao: TrackDao,
    private val scrobbleDao: ScrobbleDao,
) : ViewModel() {

    private val _allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())

    private val _sort = MutableStateFlow(PlaylistSort.NAME)
    val sort: StateFlow<PlaylistSort> = _sort.asStateFlow()

    val filter = MutableStateFlow("")

    val playlists: StateFlow<List<Playlist>> = combine(_allPlaylists, _sort, filter) { all, sort, q ->
        val filtered = if (q.isBlank()) all else all.filter { it.name.contains(q, ignoreCase = true) }
        when (sort) {
            PlaylistSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSort.TRACK_COUNT -> filtered.sortedByDescending { it.trackCount }
            PlaylistSort.NEWEST -> filtered.sortedByDescending { it.changedAt ?: it.createdAt ?: "" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _smartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = _smartPlaylists.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect {
                _allPlaylists.value = it
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

    fun setSort(sort: PlaylistSort) { _sort.value = sort }

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
