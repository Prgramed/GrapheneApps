package dev.emusic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.emusic.ui.components.FilterSheet
import dev.emusic.ui.library.albums.AlbumGridScreen
import dev.emusic.ui.library.artists.ArtistListScreen
import dev.emusic.ui.library.tracks.TrackListScreen
import dev.emusic.ui.playlists.PlaylistSort
import dev.emusic.ui.playlists.PlaylistsScreen
import dev.emusic.ui.playlists.PlaylistsViewModel

@Composable
fun LibraryScreen(
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onStatsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val artistCount by viewModel.artistCount.collectAsStateWithLifecycle()
    val albumCount by viewModel.albumCount.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val tabs = remember(artistCount, albumCount, trackCount) {
        listOf(
            if (artistCount > 0) "Artists ($artistCount)" else "Artists",
            if (albumCount > 0) "Albums ($albumCount)" else "Albums",
            if (trackCount > 0) "Tracks ($trackCount)" else "Tracks",
            "Playlists",
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val pendingDownloadCount by viewModel.pendingDownloadCount.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Playlist sort/search state
    val playlistSort by playlistsViewModel.sort.collectAsStateWithLifecycle()
    val playlistFilter by playlistsViewModel.filter.collectAsStateWithLifecycle()
    var showPlaylistSortMenu by remember { mutableStateOf(false) }
    var showPlaylistSearch by remember { mutableStateOf(false) }

    if (showFilterSheet) {
        FilterSheet(
            currentFilter = filter,
            availableGenres = availableGenres,
            onApply = { viewModel.setFilter(it) },
            onDismiss = { showFilterSheet = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with stats icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onStatsClick) {
                Icon(Icons.Default.BarChart, contentDescription = "Listening Stats")
            }
        }

        // Sync progress bar
        syncProgress?.let { progress ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = progress.stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Download progress indicator
        if (pendingDownloadCount > 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "$pendingDownloadCount tracks pending download",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        // Sort/filter toolbar (shown on Albums tab)
        if (selectedTab == 1) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = albumSort.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        AlbumSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    viewModel.setAlbumSort(sort)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { showFilterSheet = true }) {
                    if (filter.isActive) {
                        BadgedBox(badge = { Badge { Text("${filter.activeCount}") } }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    } else {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            }
        }

        // Playlist sort/search toolbar (shown on Playlists tab)
        if (selectedTab == 3) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                if (showPlaylistSearch) {
                    OutlinedTextField(
                        value = playlistFilter,
                        onValueChange = { playlistsViewModel.filter.value = it },
                        placeholder = { Text("Search playlists…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { playlistsViewModel.filter.value = ""; showPlaylistSearch = false }) {
                                Icon(Icons.Default.FilterList, "Close")
                            }
                        },
                    )
                } else {
                    Text(
                        text = playlistSort.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = { showPlaylistSearch = true }) {
                        Icon(Icons.Default.Search, "Search playlists")
                    }
                    Box {
                        IconButton(onClick = { showPlaylistSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showPlaylistSortMenu, onDismissRequest = { showPlaylistSortMenu = false }) {
                            PlaylistSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { playlistsViewModel.setSort(s); showPlaylistSortMenu = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> ArtistListScreen(
                viewModel = viewModel,
                onArtistClick = onArtistClick,
            )
            1 -> AlbumGridScreen(
                viewModel = viewModel,
                onAlbumClick = onAlbumClick,
            )
            2 -> TrackListScreen(
                viewModel = viewModel,
            )
            3 -> PlaylistsScreen(
                onPlaylistClick = onPlaylistClick,
                viewModel = playlistsViewModel,
            )
        }
    }
}
