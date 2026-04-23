package dev.emusic.ui.library.artists

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import dev.emusic.ui.library.ArtistSort
import dev.emusic.ui.library.LibraryViewModel

@Composable
fun ArtistListScreen(
    viewModel: LibraryViewModel,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artists = viewModel.artists.collectAsLazyPagingItems()

    when {
        artists.itemCount == 0 && artists.loadState.refresh is LoadState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        artists.itemCount == 0 && artists.loadState.refresh is LoadState.NotLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No artists yet\nSync your library from Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            val currentSort by viewModel.artistSort.collectAsStateWithLifecycle()
            var showSortMenu by remember { mutableStateOf(false) }

            LazyColumn(modifier = modifier.fillMaxSize()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${artists.itemCount} artists",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                ArtistSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label) },
                                        onClick = {
                                            viewModel.setArtistSort(sort)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sort == currentSort) {
                                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                items(artists.itemCount, key = { artists.peek(it)?.id ?: it }) { index ->
                    val artist = artists[index] ?: return@items
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtistClick(artist.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        AsyncImage(
                            model = remember(artist.coverArtId) { artist.coverArtId?.let { viewModel.getCoverArtUrl(it, 100) } },
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
