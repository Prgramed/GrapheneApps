package dev.emusic.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PlaylistDetailScreen(
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val dlState by viewModel.dlState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(tracks) {
        if (tracks.isNotEmpty()) viewModel.checkDownloadState()
    }

    var contextTrack by remember { mutableStateOf<dev.emusic.domain.model.Track?>(null) }
    var playlistSheetTrack by remember { mutableStateOf<dev.emusic.domain.model.Track?>(null) }

    contextTrack?.let { track ->
        dev.emusic.ui.components.TrackContextMenu(
            track = track,
            onDismiss = { contextTrack = null },
            onPlayNext = { viewModel.queueManager.addNext(it) },
            onAddToQueue = { viewModel.queueManager.addToQueue(it) },
            onAddToPlaylist = { playlistSheetTrack = it; contextTrack = null },
            onToggleStar = { viewModel.toggleStar(it) },
            onGoToArtist = { id -> contextTrack = null; onArtistClick(id) },
            onGoToAlbum = { id -> contextTrack = null; onAlbumClick(id) },
        )
    }
    playlistSheetTrack?.let { track ->
        dev.emusic.ui.components.AddToPlaylistSheet(
            trackId = track.id,
            onDismiss = { playlistSheetTrack = null },
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offset by 2 for the header items (header + buttons)
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        viewModel.moveTrack(fromIndex, toIndex)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = playlist?.name ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                )
                val totalDuration = tracks.sumOf { it.duration }
                val minutes = totalDuration / 60
                Text(
                    text = "${tracks.size} tracks · ${minutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Play / Shuffle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.playFromTrack(0) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { viewModel.shufflePlay() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle")
                }
                IconButton(
                    onClick = { viewModel.toggleDownload() },
                    enabled = dlState != PlaylistDetailViewModel.DlState.DOWNLOADING,
                ) {
                    when (dlState) {
                        PlaylistDetailViewModel.DlState.NONE -> Icon(
                            Icons.Default.Download,
                            contentDescription = "Download playlist",
                        )
                        PlaylistDetailViewModel.DlState.DOWNLOADING -> {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        PlaylistDetailViewModel.DlState.DONE -> Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Remove downloads",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Playlist size info
        item {
            val totalSize = tracks.sumOf { it.size }
            val sizeText = when {
                totalSize >= 1_073_741_824 -> "%.1f GB".format(totalSize / 1_073_741_824.0)
                totalSize >= 1_048_576 -> "%.0f MB".format(totalSize / 1_048_576.0)
                else -> "$totalSize bytes"
            }
            val downloadedCount = tracks.count { it.localPath != null }
            Text(
                text = "${tracks.size} tracks \u2022 $sizeText" +
                    if (downloadedCount > 0) " \u2022 $downloadedCount downloaded" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (downloadStatus.isNotBlank()) {
                Text(
                    text = downloadStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }

        // Track list — simple rows (no drag reorder for performance with large playlists)
        items(tracks.size, key = { "${tracks[it].id}_$it" }) { index ->
            val track = tracks[index]
            val coverUrl = remember(track.albumId) { viewModel.getCoverArtUrl(track.albumId, 100) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewModel.playFromTrack(index) },
                            onLongClick = { contextTrack = track },
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = { viewModel.removeTrack(index) },
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

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
