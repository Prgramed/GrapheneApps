package dev.egallery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val selectedMediaType by viewModel.selectedMediaType.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { viewModel.query.value = it },
                    onSearch = {
                        viewModel.saveRecentSearch(it)
                        expanded = false
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text("Search photos…") },
                    leadingIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.query.value = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Recent searches dropdown
            if (query.isEmpty() && recentSearches.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = { viewModel.clearRecentSearches() }) {
                        Text("Clear recent searches")
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentSearches.forEach { recent ->
                            AssistChip(
                                onClick = {
                                    viewModel.query.value = recent
                                    expanded = false
                                },
                                label = { Text(recent) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.padding(0.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Filter chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Photo/Video type filter
            FilterChip(
                selected = selectedMediaType == "PHOTO",
                onClick = { viewModel.setMediaTypeFilter(if (selectedMediaType == "PHOTO") null else "PHOTO") },
                label = { Text("Photos") },
                leadingIcon = { Icon(Icons.Default.Photo, null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = selectedMediaType == "VIDEO",
                onClick = { viewModel.setMediaTypeFilter(if (selectedMediaType == "VIDEO") null else "VIDEO") },
                label = { Text("Videos") },
                leadingIcon = { Icon(Icons.Default.Videocam, null, modifier = Modifier.size(18.dp)) },
            )
            // Tag filter chips (show first 10)
            availableTags.take(10).forEach { tag ->
                FilterChip(
                    selected = selectedTagId == tag.id,
                    onClick = { viewModel.setTagFilter(if (selectedTagId == tag.id) null else tag.id) },
                    label = { Text(tag.name) },
                )
            }
        }

        if (searching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (results.isEmpty() && query.length >= 2 && !searching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "No results for \"$query\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (results.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results, key = { it.nasId }) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onPhotoClick(item.nasId) },
                    ) {
                        AsyncImage(
                            model = remember(item.nasId, item.cacheKey) {
                                viewModel.thumbnailUrl(item.nasId, item.cacheKey)
                            },
                            contentDescription = item.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}
