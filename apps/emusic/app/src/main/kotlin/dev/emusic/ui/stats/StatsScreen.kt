package dev.emusic.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Listening Stats") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                showMenu = false
                                viewModel.exportCsv(context)
                            },
                        )
                    }
                }
            },
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Time range chips
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimeRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.timeRange == range,
                            onClick = { viewModel.setTimeRange(range) },
                            label = { Text(range.label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Listening time card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val hours = state.totalListeningMs / 3_600_000
                        val minutes = (state.totalListeningMs % 3_600_000) / 60_000
                        Text(
                            text = if (hours > 0) "$hours hours" else "$minutes minutes",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "of music listened",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Daily bar chart
            if (state.dailyListening.isNotEmpty()) {
                item {
                    Text("Last 30 Days", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    DailyBarChart(
                        data = state.dailyListening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Most played tracks
            if (state.topTracks.isNotEmpty()) {
                item {
                    Text("Most Played Tracks", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                items(state.topTracks.size.coerceAtMost(20), key = { state.topTracks[it].trackId }) { index ->
                    val track = state.topTracks[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        AsyncImage(
                            model = viewModel.getCoverArtUrl(track.albumId),
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
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${track.count} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Most played artists
            if (state.topArtists.isNotEmpty()) {
                item {
                    Text("Most Played Artists", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                items(state.topArtists.size.coerceAtMost(10), key = { state.topArtists[it].artistId }) { index ->
                    val artist = state.topArtists[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtistClick(artist.artistId) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${artist.count} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Most played albums
            if (state.topAlbums.isNotEmpty()) {
                item {
                    Text("Most Played Albums", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                items(state.topAlbums.size.coerceAtMost(10), key = { state.topAlbums[it].albumId }) { index ->
                    val album = state.topAlbums[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album.albumId) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        AsyncImage(
                            model = viewModel.getCoverArtUrl(album.albumId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${album.count} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // Top genres
            if (state.topGenres.isNotEmpty()) {
                item {
                    Text("Top Genres", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    GenreBarChart(
                        genres = state.topGenres,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Listening streaks
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Current streak: ${state.currentStreak} days",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Longest: ${state.longestStreak} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // First listen
            if (state.firstScrobble != null) {
                item {
                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        .format(Date(state.firstScrobble!!.timestamp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Your first listen",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DailyBarChart(
    data: List<dev.emusic.data.db.dao.DailyListening>,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val maxMs = data.maxOf { it.totalMs }.toFloat().coerceAtLeast(1f)
        val barWidth = size.width / data.size.coerceAtLeast(1) * 0.8f
        val gap = size.width / data.size.coerceAtLeast(1) * 0.2f

        data.forEachIndexed { index, day ->
            val barHeight = (day.totalMs / maxMs) * (size.height - 16f)
            val x = index * (barWidth + gap) + gap / 2
            val y = size.height - barHeight

            drawRect(
                color = primaryColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
private fun GenreBarChart(
    genres: List<dev.emusic.data.db.dao.GenrePlayCount>,
    modifier: Modifier = Modifier,
) {
    val maxCount = genres.maxOfOrNull { it.count }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier) {
        genres.forEach { genre ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = genre.genre,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val fraction = genre.count / maxCount
                val barColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp),
                ) {
                    drawRect(
                        color = barColor,
                        size = Size(size.width * fraction, size.height),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${genre.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
