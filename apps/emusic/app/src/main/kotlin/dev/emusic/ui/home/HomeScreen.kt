package dev.emusic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.emusic.domain.model.Album

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAlbumClick: (String) -> Unit,
    onGenreBrowse: () -> Unit = {},
    onRecentlyPlayed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.isLoading && uiState.data.recentlyAdded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Quick Mix button
                item {
                    FilledTonalButton(
                        onClick = { viewModel.playQuickMix() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Quick Mix", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Genre chips
                if (uiState.topGenres.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.topGenres, key = { it.genre }) { genre ->
                                AssistChip(
                                    onClick = { onGenreBrowse() },
                                    label = { Text(genre.genre) },
                                )
                            }
                        }
                    }
                }

                // You might like (suggestions)
                uiState.suggestions?.let { albums ->
                    item {
                        AlbumSection(
                            title = "You Might Like",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                val data = uiState.data

                data.jumpBackIn?.let { albums ->
                    item {
                        AlbumSection(
                            title = "Jump Back In",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                            onSeeAll = onRecentlyPlayed,
                        )
                    }
                }

                data.recentlyAdded?.let { albums ->
                    item {
                        AlbumSection(
                            title = "Recently Added",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                data.frequentlyPlayed?.let { albums ->
                    item {
                        AlbumSection(
                            title = "Frequently Played",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                data.starredAlbums?.let { albums ->
                    item {
                        AlbumSection(
                            title = "Starred Albums",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                data.topRated?.let { albums ->
                    item {
                        AlbumSection(
                            title = "Top Rated",
                            albums = albums,
                            getCoverArtUrl = { viewModel.getCoverArtUrl(it) },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AlbumSection(
    title: String,
    albums: List<Album>,
    getCoverArtUrl: (String) -> String,
    onAlbumClick: (String) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSeeAll() },
            )
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clickable { onAlbumClick(album.id) },
            ) {
                val coverUrl = remember(album.coverArtId) {
                    album.coverArtId?.let { getCoverArtUrl(it) }
                }
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
