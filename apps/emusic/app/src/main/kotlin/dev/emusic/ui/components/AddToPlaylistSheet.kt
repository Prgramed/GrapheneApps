package dev.emusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.domain.model.Playlist
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _membershipIds = MutableStateFlow<Set<String>>(emptySet())
    val membershipIds: StateFlow<Set<String>> = _membershipIds.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { _playlists.value = it }
        }
    }

    fun loadMembership(trackId: String) {
        viewModelScope.launch {
            val ids = playlistDao.getPlaylistIdsContainingTrack(trackId)
            _membershipIds.value = ids.toSet()
        }
    }

    fun toggleTrackInPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            val current = _membershipIds.value
            if (playlistId in current) {
                // Remove — need to find the track's index in the playlist
                try {
                    val tracks = playlistRepository.observePlaylistTracks(playlistId).first()
                    val index = tracks.indexOfFirst { t -> t.id == trackId }
                    if (index >= 0) {
                        playlistRepository.removeTrackFromPlaylist(playlistId, index)
                        _membershipIds.value = current - playlistId
                    }
                } catch (_: Exception) { }
            } else {
                // Add
                try {
                    playlistRepository.addTracksToPlaylist(playlistId, listOf(trackId))
                    _membershipIds.value = current + playlistId
                } catch (_: Exception) { }
            }
        }
    }

    fun getCoverArtUrl(id: String): String = libraryRepository.getCoverArtUrl(id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    trackId: String,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val membershipIds by viewModel.membershipIds.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(trackId) {
        viewModel.loadMembership(trackId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Add to playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (playlists.isEmpty()) {
            Text(
                text = "No playlists yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    val isMember = playlist.id in membershipIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleTrackInPlaylist(playlist.id, trackId)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        AsyncImage(
                            model = playlist.coverArtId?.let { viewModel.getCoverArtUrl(it) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = if (isMember) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (isMember) "In playlist" else "Not in playlist",
                            tint = if (isMember) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
