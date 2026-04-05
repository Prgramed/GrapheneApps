package dev.egallery.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import dev.egallery.domain.model.MediaItem
import dev.egallery.domain.model.MediaType
import dev.egallery.domain.model.StorageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoViewerScreen(
    onBack: () -> Unit,
    onVideoPlay: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val timelineIds by viewModel.timelineIds.collectAsState()
    val currentItem by viewModel.currentItem.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    var uiVisible by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf<String?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var slideshowActive by remember { mutableStateOf(false) }
    val exifData by viewModel.exifData.collectAsState()
    val deleteEvent by viewModel.deleteEvent.collectAsState()
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }

    // Navigate back after delete (one-shot event)
    LaunchedEffect(deleteEvent) {
        if (deleteEvent) {
            viewModel.consumeDeleteEvent()
            onBack()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete photo?") },
            text = { Text("This will move the photo to trash.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCurrentItem()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
    val dismissThreshold = 300f

    if (timelineIds.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val initialIndex = remember(timelineIds, viewModel.initialNasId) {
        timelineIds.indexOf(viewModel.initialNasId).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { timelineIds.size }

    // Slideshow auto-advance (3 seconds per photo)
    LaunchedEffect(slideshowActive) {
        if (slideshowActive) {
            while (true) {
                kotlinx.coroutines.delay(3000L)
                val nextPage = pagerState.currentPage + 1
                if (nextPage < timelineIds.size) {
                    pagerState.animateScrollToPage(nextPage)
                } else {
                    slideshowActive = false
                }
            }
        }
    }

    // Load item detail when page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page in timelineIds.indices) {
                viewModel.loadItem(timelineIds[page])
            }
        }
    }

    val dismissProgress = (dismissOffsetY / dismissThreshold).coerceIn(0f, 1f)
    val bgAlpha = 1f - dismissProgress * 0.5f
    val contentScale = 1f - dismissProgress * 0.1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { uiVisible = !uiVisible }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissOffsetY > dismissThreshold) {
                            onBack()
                        }
                        dismissOffsetY = 0f
                    },
                    onDragCancel = { dismissOffsetY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || dismissOffsetY > 0) {
                            dismissOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
                        }
                    },
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = dismissOffsetY.dp)
                .graphicsLayer { scaleX = contentScale; scaleY = contentScale },
            key = { timelineIds.getOrElse(it) { it } },
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(dampingRatio = 0.95f, stiffness = 600f),
            ),
        ) { page ->
            val nasId = timelineIds.getOrElse(page) { return@HorizontalPager }
            val item = if (page == pagerState.currentPage) currentItem else null

            Box(contentAlignment = Alignment.Center) {
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
                ZoomableImage(
                    model = item?.let { viewModel.imageUrl(it) },
                    contentDescription = item?.filename,
                    sharedModifier = sharedModifier,
                )

                // Video play overlay
                if (item?.mediaType == MediaType.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play video",
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { onVideoPlay(nasId) },
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(top = 40.dp, bottom = 8.dp, start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    viewModel.loadExifData()
                    showInfoSheet = true
                }) {
                    Icon(Icons.Default.Info, "Info", tint = Color.White)
                }
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        if (currentItem?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favorite",
                        tint = if (currentItem?.isFavorite == true) Color.Red else Color.White,
                    )
                }
                IconButton(onClick = { viewModel.shareCurrentItem() }) {
                    Icon(Icons.Default.Share, "Share", tint = Color.White)
                }
                if (currentItem?.mediaType != MediaType.VIDEO) {
                    IconButton(onClick = { currentItem?.nasId?.let { onEdit(it) } }) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                }
                // Overflow menu for Copy/Move
                var showOverflow by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showOverflow = !showOverflow }) {
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Copy to...") },
                            onClick = {
                                showOverflow = false
                                showFolderPicker = "copy"
                            },
                        )
                        if (currentItem?.localPath != null && !currentItem!!.localPath!!.startsWith("content://")) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Move to...") },
                                onClick = {
                                    showOverflow = false
                                    showFolderPicker = "move"
                                },
                            )
                        }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Slideshow") },
                            onClick = {
                                showOverflow = false
                                slideshowActive = !slideshowActive
                            },
                        )
                    }
                }
            }
        }

        // Bottom info bar
        AnimatedVisibility(
            visible = uiVisible && currentItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            currentItem?.let { item ->
                BottomInfoBar(item = item)
            }
        }

        // Download FAB
        AnimatedVisibility(
            visible = uiVisible && currentItem?.storageStatus == StorageStatus.NAS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            val nasId = currentItem?.nasId ?: return@AnimatedVisibility
            when (downloadState) {
                is DownloadState.Downloading -> {
                    val progress = (downloadState as DownloadState.Downloading).progress
                    if (progress < 0f) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = Color.White,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(56.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
                is DownloadState.Error -> {
                    FloatingActionButton(
                        onClick = { viewModel.downloadForOffline(nasId) },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Icon(Icons.Default.CloudDownload, "Retry download")
                    }
                }
                else -> {
                    FloatingActionButton(
                        onClick = { viewModel.downloadForOffline(nasId) },
                    ) {
                        Icon(Icons.Default.CloudDownload, "Download")
                    }
                }
            }
        }
    }

    // Photo info sheet
    if (showInfoSheet && currentItem != null) {
        PhotoInfoSheet(
            item = currentItem!!,
            exifData = exifData,
            onDismiss = { showInfoSheet = false },
        )
    }

    // Folder picker for copy/move
    if (showFolderPicker != null) {
        dev.egallery.ui.components.FolderPickerSheet(
            folders = viewModel.getDeviceFolders(),
            onFolderSelected = { destDir ->
                when (showFolderPicker) {
                    "copy" -> viewModel.copyCurrentTo(destDir)
                    "move" -> viewModel.moveCurrentTo(destDir)
                }
                showFolderPicker = null
            },
            onDismiss = { showFolderPicker = null },
        )
    }
}

@Composable
private fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    sharedModifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scale > 1f) {
                if (scale > 1f) {
                    // When zoomed: handle pan + pinch
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offset = Offset(x = offset.x + pan.x, y = offset.y + pan.y)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                } else {
                    // At 1x: only detect pinch-to-zoom via multi-touch, let pager handle single-finger swipe
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                // Two fingers — handle zoom
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    scale = (scale * zoomChange).coerceIn(1f, 4f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = sharedModifier
                .then(Modifier.fillMaxSize())
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun BottomInfoBar(item: MediaItem) {
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.filename,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Text(
            text = dateFormat.format(Date(item.captureDate)),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        StorageChip(status = item.storageStatus)
    }
}

@Composable
private fun StorageChip(status: StorageStatus) {
    val (label, color) = when (status) {
        StorageStatus.SYNCED -> "Synced" to Color(0xFF4CAF50)
        StorageStatus.NAS -> "NAS only" to Color(0xFF9E9E9E)
        StorageStatus.DEVICE -> "Device only" to Color(0xFFFF9800)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .background(color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
