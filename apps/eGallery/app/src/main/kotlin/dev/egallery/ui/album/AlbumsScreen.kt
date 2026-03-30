package dev.egallery.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import dev.egallery.domain.model.Album

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Album?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Album?>(null) }
    var showOptionsSheet by remember { mutableStateOf<Album?>(null) }

    // Create album dialog
    if (showCreateDialog) {
        TextFieldDialog(
            title = "Create album",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // Rename dialog
    showRenameDialog?.let { album ->
        TextFieldDialog(
            title = "Rename album",
            initialValue = album.name,
            confirmLabel = "Rename",
            onConfirm = { name ->
                viewModel.renameAlbum(album.id, name)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null },
        )
    }

    // Delete confirmation
    showDeleteDialog?.let { album ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete album?") },
            text = { Text("\"${album.name}\" will be deleted. Photos in the album will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(album.id)
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            },
        )
    }

    // Long-press options sheet
    showOptionsSheet?.let { album ->
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                TextButton(
                    onClick = {
                        showOptionsSheet = null
                        showRenameDialog = album
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Rename", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = {
                        showOptionsSheet = null
                        showDeleteDialog = album
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create album")
            }
        },
    ) { padding ->
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoAlbum,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No albums yet\nTap + to create one",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        coverUrl = remember(album.coverPhotoId) {
                            viewModel.coverThumbnailUrl(album.coverPhotoId)
                        },
                        onClick = { onAlbumClick(album.id) },
                        onLongClick = { showOptionsSheet = album },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Album,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            maxLines = 1,
        )
        Text(
            text = "${album.photoCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
