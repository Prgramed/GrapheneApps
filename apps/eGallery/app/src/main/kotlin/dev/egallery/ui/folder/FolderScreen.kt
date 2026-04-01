package dev.egallery.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.StorageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun FolderScreen(
    onPhotoClick: (String) -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val photosFlow by viewModel.photos.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val showIgnored by viewModel.showIgnored.collectAsState()
    val isRoot = breadcrumbs.size <= 1

    androidx.activity.compose.BackHandler(enabled = breadcrumbs.size > 1) {
        viewModel.navigateUp()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row with filter toggle (only at root)
        if (isRoot) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { viewModel.toggleShowIgnored() }) {
                    Icon(
                        imageVector = if (showIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showIgnored) "Hide app folders" else "Show app folders",
                        tint = if (showIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (breadcrumbs.size > 1) {
            BreadcrumbBar(
                breadcrumbs = breadcrumbs,
                onBreadcrumbClick = { index -> viewModel.navigateToBreadcrumb(index) },
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (folders.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(folders, key = { it.id }) { folder ->
                        val coverModel: Any? = when {
                            folder.coverLocalPath != null && folder.coverLocalPath.startsWith("content://") ->
                                android.net.Uri.parse(folder.coverLocalPath)
                            folder.coverLocalPath != null ->
                                java.io.File(folder.coverLocalPath)
                            folder.coverNasId != null && folder.coverCacheKey.isNotBlank() ->
                                viewModel.thumbnailUrl(folder.coverNasId, folder.coverCacheKey)
                            else -> null
                        }
                        FolderRow(
                            folder = folder,
                            coverModel = coverModel,
                            onClick = { viewModel.navigateToFolder(folder.id, folder.name, folder.localDirPath) },
                        )
                    }
                }
            }

            PhotoGrid(
                photosFlow = photosFlow,
                viewModel = viewModel,
                onPhotoClick = onPhotoClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<Breadcrumb>,
    onBreadcrumbClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        breadcrumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Navigate",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = crumb.name,
                style = if (index == breadcrumbs.lastIndex) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (index == breadcrumbs.lastIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .then(if (index < breadcrumbs.lastIndex) Modifier.clickable { onBreadcrumbClick(index) } else Modifier)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    coverModel: Any?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = remember(coverModel) {
                    ImageRequest.Builder(context)
                        .data(coverModel)
                        .size(192)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                modifier = Modifier.size(48.dp).padding(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (folder.photoCount > 0) {
                Text(
                    text = "${folder.photoCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = "Open folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoGrid(
    photosFlow: Flow<List<MediaItem>>,
    viewModel: FolderViewModel,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photos by photosFlow.collectAsState(initial = emptyList())

    if (photos.isEmpty()) {
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.background(androidx.compose.ui.graphics.Color.Black),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(photos, key = { it.nasId }) { item ->
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onPhotoClick(item.nasId) },
            ) {
                AsyncImage(
                    model = remember(item.nasId, item.cacheKey, item.localPath) {
                        ImageRequest.Builder(context)
                            .data(viewModel.thumbnailModel(item))
                            .size(360)
                            .build()
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
