package dev.emusic.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.download.DownloadManager
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import dev.emusic.playback.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    val queueManager: QueueManager,
    private val trackDao: TrackDao,
    private val scrobbleDao: ScrobbleDao,
    private val downloadManager: DownloadManager,
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"] ?: ""
    val isSmart: Boolean = playlistId.startsWith("smart_")

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        if (isSmart) {
            _playlist.value = Playlist(
                id = playlistId,
                name = when (playlistId) {
                    "smart_starred" -> "Starred Tracks"
                    "smart_most_played" -> "Most Played"
                    "smart_recent" -> "Recent 50"
                    "smart_never_played" -> "Never Played"
                    else -> "Smart Playlist"
                },
            )
            loadSmartPlaylistTracks()
        } else {
            viewModelScope.launch {
                _playlist.value = playlistRepository.getPlaylist(playlistId)
            }
            viewModelScope.launch {
                // Observe Room for reactive updates
                playlistRepository.observePlaylistTracks(playlistId).collect {
                    _tracks.value = it
                }
            }
            viewModelScope.launch {
                // Fetch tracks from API so the Room table is populated
                try {
                    playlistRepository.refreshPlaylistTracks(playlistId)
                } catch (_: Exception) { }
            }
        }
    }

    private fun loadSmartPlaylistTracks() {
        viewModelScope.launch {
            val tracks = when (playlistId) {
                "smart_starred" -> trackDao.observeStarred().first().map { it.toDomain() }
                "smart_most_played" -> {
                    val ids = scrobbleDao.getTopTrackIdsAllTime(50).first()
                    ids.mapNotNull { id -> trackDao.getById(id)?.toDomain() }
                }
                "smart_recent" -> {
                    val ids = scrobbleDao.getRecentDistinctTrackIds(50).first()
                    ids.mapNotNull { id -> trackDao.getById(id)?.toDomain() }
                }
                "smart_never_played" -> trackDao.observeNeverPlayed().first().map { it.toDomain() }
                else -> emptyList()
            }
            _tracks.value = tracks
        }
    }

    fun getCoverArtUrl(id: String, size: Int = 300): String =
        libraryRepository.getCoverArtUrl(id, size)

    fun playFromTrack(index: Int) {
        val trackList = _tracks.value
        if (trackList.isNotEmpty()) {
            queueManager.play(trackList, index)
        }
    }

    // Pinned = "download this playlist" toggle
    val isPinned: StateFlow<Boolean> = playlistDao.observeAll()
        .map { all -> all.find { it.id == playlistId }?.pinned ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Download progress: count of tracks with localPath / total tracks
    private val _downloadProgress = MutableStateFlow("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    init {
        // Observe download progress reactively
        if (!isSmart) {
            viewModelScope.launch {
                playlistDao.observePlaylistTracks(playlistId).collect { trackEntities ->
                    val total = trackEntities.size
                    val done = trackEntities.count { it.localPath != null }
                    _downloadProgress.value = when {
                        total == 0 -> ""
                        done >= total -> "All downloaded"
                        done > 0 -> "$done / $total downloaded"
                        else -> ""
                    }
                }
            }
        }
    }

    fun togglePinned() {
        viewModelScope.launch {
            val current = isPinned.value
            playlistDao.setPinned(playlistId, !current)
            if (current) {
                // Unpinning — cancel pending downloads for this playlist's tracks
                val tracks = _tracks.value
                for (track in tracks) {
                    downloadManager.cancel(track.id)
                }
            }
            // If pinning, the DownloadQueueProcessor will pick it up automatically
        }
    }

    fun removeDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Unpin first
            playlistDao.setPinned(playlistId, false)
            val tracks = _tracks.value
            for (track in tracks) {
                val entity = trackDao.getById(track.id) ?: continue
                val path = entity.localPath ?: continue
                try { java.io.File(path).delete() } catch (_: Exception) { }
                trackDao.updateLocalPath(track.id, null)
                downloadManager.cancel(track.id)
            }
        }
    }

    fun shufflePlay() {
        val trackList = _tracks.value.ifEmpty { return }
        queueManager.play(trackList.shuffled(), 0)
    }

    fun toggleStar(track: dev.emusic.domain.model.Track) {
        viewModelScope.launch {
            if (track.starred) libraryRepository.unstarTrack(track.id)
            else libraryRepository.starTrack(track.id)
        }
    }

    fun removeTrack(index: Int) {
        val previous = _tracks.value
        // Optimistic UI update
        _tracks.value = previous.toMutableList().apply { removeAt(index) }
        viewModelScope.launch {
            try {
                playlistRepository.removeTrackFromPlaylist(playlistId, index)
            } catch (_: Exception) {
                // Rollback on failure
                _tracks.value = previous
            }
        }
    }

    fun moveTrack(from: Int, to: Int) {
        val previous = _tracks.value
        val current = previous.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        _tracks.value = current
        viewModelScope.launch {
            try {
                playlistRepository.reorderPlaylistTracks(playlistId, current.map { it.id })
            } catch (_: Exception) {
                // Rollback to previous order on failure
                _tracks.value = previous
            }
        }
    }
}
