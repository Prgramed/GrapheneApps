package dev.emusic.ui.library.tracks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import dev.emusic.domain.model.Track
import dev.emusic.ui.components.TrackContextMenu
import dev.emusic.ui.library.LibraryViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListScreen(
    viewModel: LibraryViewModel,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var contextTrack by remember { mutableStateOf<Track?>(null) }
    var showPlaylistSheet by remember { mutableStateOf<Track?>(null) }

    contextTrack?.let { track ->
        TrackContextMenu(
            track = track,
            onDismiss = { contextTrack = null },
            onPlayNext = { viewModel.queueManager.addNext(it) },
            onAddToQueue = { viewModel.queueManager.addToQueue(it) },
            onAddToPlaylist = { showPlaylistSheet = it; contextTrack = null },
            onToggleStar = { viewModel.toggleStar(it) },
            onGoToArtist = { id -> contextTrack = null; onArtistClick(id) },
            onGoToAlbum = { id -> contextTrack = null; onAlbumClick(id) },
        )
    }

    showPlaylistSheet?.let { track ->
        dev.emusic.ui.components.AddToPlaylistSheet(
            trackId = track.id,
            onDismiss = { showPlaylistSheet = null },
        )
    }
    val tracks = viewModel.tracks.collectAsLazyPagingItems()
    val totalCount by viewModel.trackCount.collectAsStateWithLifecycle()

    when {
        tracks.loadState.refresh is LoadState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        tracks.itemCount == 0 && tracks.loadState.refresh is LoadState.NotLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tracks yet\nSync your library from Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item {
                    Text(
                        text = "$totalCount tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(tracks.itemCount, key = { tracks.peek(it)?.id ?: it }) { index ->
                    val track = tracks[index] ?: return@items
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { viewModel.playTrack(track) },
                                onLongClick = { contextTrack = track },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        AsyncImage(
                            model = track.albumId.let { viewModel.getCoverArtUrl(it, 100) },
                            contentDescription = track.album,
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
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
