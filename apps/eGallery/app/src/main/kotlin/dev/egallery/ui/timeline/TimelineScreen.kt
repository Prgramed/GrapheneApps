package dev.egallery.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.platform.LocalContext
import dev.egallery.domain.model.StorageStatus
import dev.egallery.sync.SyncState
import androidx.compose.ui.res.stringResource
import dev.egallery.ui.album.AlbumPickerSheet
import dev.egallery.ui.components.FolderPickerSheet
import dev.egallery.ui.components.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TimelineScreen(
    onPhotoClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    pendingUploadCount: Int = 0,
    isNasReachable: Boolean = true,
    pickerMode: Boolean = false,
    scrollToTopTrigger: Int = 0,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val items = viewModel.timeline.collectAsLazyPagingItems()
    val syncState by viewModel.syncState.collectAsState()
    val isSyncing = syncState is SyncState.Syncing
    val selectedNasIds by viewModel.selectedNasIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val photoCount by viewModel.photoCount.collectAsState()
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset,
    )

    // Scroll to top when tab is re-tapped
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.animateScrollToItem(0)
        }
    }

    // Save scroll position when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.savedScrollIndex = gridState.firstVisibleItemIndex
            viewModel.savedScrollOffset = gridState.firstVisibleItemScrollOffset
        }
    }

    // Viewport-priority thumbnail prefetch: when scroll settles, prefetch visible items
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val visibleNasIds = gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val item = items.peek(info.index)
                    (item as? TimelineItem.PhotoCell)?.item?.nasId
                }
                viewModel.prefetchVisibleThumbnails(visibleNasIds.take(6))
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                TopAppBar(
                    title = { Text("${selectedNasIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAlbumPicker = true }) {
                            Icon(Icons.Default.LibraryAdd, "Add to Album")
                        }
                        IconButton(onClick = { showMovePicker = true }) {
                            Icon(Icons.Default.DriveFileMove, "Move to folder")
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    },
                )
            }
            if (!isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (pickerMode) "Select a photo" else stringResource(dev.egallery.R.string.app_name))
                            if (photoCount > 0 && !pickerMode) {
                                Text(
                                    text = "$photoCount photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // Sync is done from Settings, not Timeline
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OfflineBanner(
                pendingCount = pendingUploadCount,
                isNasReachable = isNasReachable,
            )
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                items.loadState.refresh is LoadState.Error -> {
                    val error = (items.loadState.refresh as LoadState.Error).error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error loading photos",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = error.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Button(onClick = { items.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No photos yet\nSync from Settings to load from NAS",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    Box {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            count = items.itemCount,
                            key = { index ->
                                when (val item = items.peek(index)) {
                                    is TimelineItem.DateHeader -> "header_${item.label}"
                                    is TimelineItem.PhotoCell -> "photo_${item.item.nasId}"
                                    null -> "item_$index"
                                }
                            },
                            span = { index ->
                                when (items.peek(index)) {
                                    is TimelineItem.DateHeader -> GridItemSpan(maxLineSpan)
                                    else -> GridItemSpan(1)
                                }
                            },
                        ) { index ->
                            when (val item = items[index]) {
                                is TimelineItem.DateHeader -> {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1A1A1A))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }

                                is TimelineItem.PhotoCell -> {
                                    val nasId = item.item.nasId
                                    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedElement(
                                                rememberSharedContentState(key = "photo_$nasId"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                    PhotoGridCell(
                                        item = item.item,
                                        thumbnailModel = remember(nasId, item.item.cacheKey, item.item.localPath) {
                                            viewModel.thumbnailModel(item.item)
                                        },
                                        isSelected = nasId in selectedNasIds,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                viewModel.toggleSelection(nasId)
                                            } else {
                                                onPhotoClick(nasId)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(nasId) },
                                        sharedModifier = sharedModifier,
                                    )
                                }

                                null -> {}
                            }
                        }
                    }

                    // Fast scroll bar overlay
                    val scrollbarAlpha by animateFloatAsState(
                        targetValue = if (gridState.isScrollInProgress) 1f else 0f,
                        animationSpec = tween(if (gridState.isScrollInProgress) 150 else 1500),
                        label = "scrollbar",
                    )
                    if (scrollbarAlpha > 0f) {
                        val totalItems = items.itemCount.coerceAtLeast(1)
                        val firstVisible = gridState.firstVisibleItemIndex
                        val fraction = firstVisible.toFloat() / totalItems
                        val barHeight = 48.dp

                        // Get current month label from first visible item
                        val monthLabel = (items.peek(firstVisible) as? TimelineItem.DateHeader)?.label
                            ?: (items.peek((firstVisible - 1).coerceAtLeast(0)) as? TimelineItem.DateHeader)?.label

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(40.dp)
                                .alpha(scrollbarAlpha)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            // Scroll thumb
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = (fraction * 300).dp.coerceAtMost(280.dp))
                                    .size(width = 4.dp, height = barHeight)
                                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
                            )
                        }

                        // Month label popup
                        if (monthLabel != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 48.dp, top = (fraction * 300).dp.coerceAtMost(280.dp))
                                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .alpha(scrollbarAlpha),
                            ) {
                                Text(
                                    text = monthLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                        }
                    }
                    }
                }

            }
        }
        }
    }

    // Album picker bottom sheet
    if (showAlbumPicker) {
        AlbumPickerSheet(
            albumsFlow = viewModel.albums,
            onAlbumSelected = { albumId ->
                viewModel.addSelectedToAlbum(albumId)
                showAlbumPicker = false
            },
            onDismiss = { showAlbumPicker = false },
        )
    }

    // Folder picker for move
    if (showMovePicker) {
        FolderPickerSheet(
            folders = viewModel.getDeviceFolders(),
            onFolderSelected = { destDir ->
                viewModel.moveSelectedTo(destDir)
                showMovePicker = false
            },
            onDismiss = { showMovePicker = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridCell(
    item: dev.egallery.domain.model.MediaItem,
    thumbnailModel: Any,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    sharedModifier: Modifier = Modifier,
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "selection_scale",
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(selectionScale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = remember(thumbnailModel) {
                ImageRequest.Builder(context)
                    .data(thumbnailModel)
                    .size(360)
                    .memoryCacheKey("thumb_${item.nasId}")
                    .diskCacheKey("thumb_${item.nasId}")
                    .build()
            },
            contentDescription = item.filename,
            contentScale = ContentScale.Crop,
            modifier = sharedModifier
                .then(Modifier.fillMaxSize())
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        // Video / Live Photo indicator
        if (item.mediaType == dev.egallery.domain.model.MediaType.VIDEO || item.mediaType == dev.egallery.domain.model.MediaType.LIVE_PHOTO) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        }

        // Upload status overlay
        if (item.storageStatus == StorageStatus.UPLOAD_PENDING) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Uploading",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        if (item.storageStatus == StorageStatus.UPLOAD_FAILED) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Upload failed",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        // Selection checkmark overlay
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
