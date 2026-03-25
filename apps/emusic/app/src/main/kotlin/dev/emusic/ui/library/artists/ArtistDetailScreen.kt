package dev.emusic.ui.library.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val artistInfo by viewModel.artistInfo.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
            onGoToArtist = null,
            onGoToAlbum = { id -> contextTrack = null; onAlbumClick(id) },
        )
    }
    playlistTrack?.let { track ->
        dev.emusic.ui.components.AddToPlaylistSheet(
            trackId = track.id,
            onDismiss = { playlistTrack = null },
        )
    }

    if (isLoading && artist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Header image + artist name
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f),
            ) {
                val imageUrl = artistInfo?.largeImageUrl
                    ?: artistInfo?.mediumImageUrl
                    ?: albums.firstOrNull()?.coverArtId?.let { viewModel.getCoverArtUrl(it) }

                AsyncImage(
                    model = imageUrl,
                    contentDescription = artist?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f,
                            ),
                        ),
                )
                Text(
                    text = artist?.name ?: "",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                )
            }
        }

        // Shuffle All button
        item {
            Button(
                onClick = { viewModel.shuffleAll() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Shuffle All")
            }
        }

        // Top Tracks
        if (topSongs.isNotEmpty()) {
            item {
                SectionHeader("Top Tracks")
            }
            val displaySongs = topSongs.take(10)
            items(displaySongs.size, key = { displaySongs[it].id }) { index ->
                val track = displaySongs[index]
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
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    AsyncImage(
                        model = viewModel.getCoverArtUrl(track.albumId, 100),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
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
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (track.playCount > 0) {
                        Text(
                            text = "${track.playCount} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Discography
        if (albums.isNotEmpty()) {
            item {
                SectionHeader("Discography")
            }
            items(albums.size, key = { albums[it].id }) { index ->
                val album = albums[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = album.coverArtId?.let { viewModel.getCoverArtUrl(it, 100) },
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = listOfNotNull(
                            album.year?.toString(),
                            "${album.trackCount} tracks",
                        ).joinToString(" · ")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Similar Artists
        val similarArtists = artistInfo?.similarArtists.orEmpty()
        if (similarArtists.isNotEmpty()) {
            item {
                SectionHeader("Similar Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(similarArtists) { similar ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(80.dp)
                                .clickable { onArtistClick(similar.id) },
                        ) {
                            AsyncImage(
                                model = similar.coverArtId?.let { viewModel.getCoverArtUrl(it, 100) },
                                contentDescription = similar.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = similar.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Bio
        val bio = artistInfo?.biography
        if (!bio.isNullOrBlank()) {
            item {
                SectionHeader("About")
                var expanded by remember { mutableStateOf(false) }
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(if (expanded) "Show less" else "Show more")
                }
            }
        }

        // Bottom spacing
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
