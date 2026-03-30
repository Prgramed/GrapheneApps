package dev.egallery.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val album by viewModel.album.collectAsState()
    val photos by viewModel.photos.collectAsState(initial = emptyList())
    val loading by viewModel.loading.collectAsState()
    val selectedNasIds by viewModel.selectedNasIds.collectAsState()
    val isMultiSelect = selectedNasIds.isNotEmpty()

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isMultiSelect,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                TopAppBar(
                    title = { Text("${selectedNasIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.removeSelectedFromAlbum() }) {
                            Icon(Icons.Default.RemoveCircle, "Remove from album")
                        }
                    },
                )
            }
            if (!isMultiSelect) {
                TopAppBar(
                    title = { Text(album?.name ?: "Album") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No photos in this album",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(photos, key = { it.nasId }) { item ->
                    val nasId = item.nasId
                    val isSelected = nasId in selectedNasIds
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelect) {
                                        viewModel.toggleSelection(nasId)
                                    } else {
                                        onPhotoClick(nasId)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(nasId) },
                            ),
                    ) {
                        AsyncImage(
                            model = remember(nasId, item.cacheKey) {
                                viewModel.thumbnailUrl(nasId, item.cacheKey)
                            },
                            contentDescription = item.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White, CircleShape),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
