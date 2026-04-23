package dev.emusic.ui.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.domain.model.QueueItem
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import dev.emusic.playback.QueueManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    val queueManager: QueueManager,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val queue: StateFlow<List<QueueItem>> = queueManager.queue
    val currentIndex: StateFlow<Int> = queueManager.currentIndex

    fun playAtIndex(index: Int) = queueManager.setCurrentIndex(index)
    fun removeAtIndex(index: Int) = queueManager.remove(index)
    fun getCoverArtUrl(id: String): String = libraryRepository.getCoverArtUrl(id, 100)

    fun toggleStar(track: dev.emusic.domain.model.Track) {
        viewModelScope.launch {
            if (track.starred) libraryRepository.unstarTrack(track.id)
            else libraryRepository.starTrack(track.id)
        }
    }

    fun saveAsPlaylist(name: String) {
        viewModelScope.launch {
            val trackIds = queueManager.queue.value.map { it.track.id }
            try {
                playlistRepository.createPlaylist(name, trackIds)
            } catch (_: Exception) { }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun QueueScreen(
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var contextTrack by remember { mutableStateOf<dev.emusic.domain.model.Track?>(null) }
    var playlistTrack by remember { mutableStateOf<dev.emusic.domain.model.Track?>(null) }

    contextTrack?.let { track ->
        dev.emusic.ui.components.TrackContextMenu(
            track = track,
            onDismiss = { contextTrack = null },
            onPlayNext = { viewModel.queueManager.addNext(it) },
            onAddToQueue = { viewModel.queueManager.addToQueue(it) },
            onAddToPlaylist = { playlistTrack = it; contextTrack = null },
            onToggleStar = { viewModel.toggleStar(it) },
            onGoToArtist = { id -> contextTrack = null; onArtistClick(id) },
            onGoToAlbum = { id -> contextTrack = null; onAlbumClick(id) },
        )
    }
    playlistTrack?.let { track ->
        dev.emusic.ui.components.AddToPlaylistSheet(
            trackId = track.id,
            onDismiss = { playlistTrack = null },
        )
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Queue as Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveAsPlaylist(name.trim())
                        showSaveDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offset by 1 for the header item
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex >= 0 && toIndex >= 0) {
            viewModel.queueManager.moveItem(fromIndex, toIndex)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (queue.isNotEmpty()) {
                    IconButton(onClick = { viewModel.queueManager.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear queue")
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Save as playlist")
                    }
                }
            }
        }

        items(queue.size, key = { queue[it].track.id }) { index ->
            ReorderableItem(reorderableState, key = queue[index].track.id) { isDragging ->
                val item = queue[index]
                val isCurrentTrack = index == currentIndex
                val elevation = if (isDragging) 4.dp else 0.dp
                androidx.compose.material3.Surface(
                    shadowElevation = elevation,
                    tonalElevation = if (isDragging) 2.dp else 0.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { viewModel.playAtIndex(index) },
                                onLongClick = { contextTrack = item.track },
                            )
                            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        // Drag handle
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(32.dp)
                                .longPressDraggableHandle()
                                .padding(4.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        AsyncImage(
                            model = viewModel.getCoverArtUrl(item.track.albumId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeAtIndex(index) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
