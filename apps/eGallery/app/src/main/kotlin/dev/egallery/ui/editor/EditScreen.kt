package dev.egallery.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    onBack: () -> Unit,
    viewModel: EditViewModel = hiltViewModel(),
) {
    val bitmap by viewModel.previewBitmap.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val dirty by viewModel.dirty.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val showCropOverlay by viewModel.showCropOverlay.collectAsState()
    val cropRect by viewModel.cropRect.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Cancel confirmation on back press
    BackHandler(enabled = dirty) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(saveResult) {
        when {
            saveResult?.isSuccess == true -> onBack()
            saveResult?.isFailure == true -> {
                val msg = saveResult?.exceptionOrNull()?.message ?: "Edit couldn't be saved — try again"
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                    }
                },
                actions = {
                    if (canUndo) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !saving && bitmap != null && dirty,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Image preview with optional crop overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else if (bitmap != null) {
                    var imageSize by remember { mutableStateOf(IntSize.Zero) }

                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .onSizeChanged { imageSize = it },
                    )

                    if (showCropOverlay && cropRect != null && imageSize != IntSize.Zero) {
                        CropOverlay(
                            rect = cropRect!!,
                            viewSize = imageSize,
                            onRectChanged = { viewModel.setCropRect(it) },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                }
            }

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = {
                    selectedTab = 0
                    viewModel.disableCropOverlay()
                }) {
                    Text("Rotate", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = {
                    selectedTab = 1
                    viewModel.enableCropOverlay()
                }) {
                    Text("Crop", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 2, onClick = {
                    selectedTab = 2
                    viewModel.disableCropOverlay()
                    viewModel.beginColorAdjust()
                }) {
                    Text("Adjust", modifier = Modifier.padding(12.dp))
                }
            }

            // Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(16.dp),
            ) {
                when (selectedTab) {
                    0 -> RotateTab(viewModel)
                    1 -> CropTab(viewModel)
                    2 -> AdjustTab(viewModel)
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(
    rect: android.graphics.RectF,
    viewSize: IntSize,
    onRectChanged: (android.graphics.RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = viewSize.width.toFloat()
    val h = viewSize.height.toFloat()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = dragAmount.x / w
                    val dy = dragAmount.y / h
                    // Move the whole rect
                    val newLeft = (rect.left + dx).coerceIn(0f, 1f - (rect.right - rect.left))
                    val newTop = (rect.top + dy).coerceIn(0f, 1f - (rect.bottom - rect.top))
                    val rw = rect.right - rect.left
                    val rh = rect.bottom - rect.top
                    onRectChanged(android.graphics.RectF(newLeft, newTop, newLeft + rw, newTop + rh))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height

            // Dim outside
            val dimColor = Color.Black.copy(alpha = 0.5f)
            drawRect(dimColor, Offset.Zero, Size(size.width, top)) // top
            drawRect(dimColor, Offset(0f, bottom), Size(size.width, size.height - bottom)) // bottom
            drawRect(dimColor, Offset(0f, top), Size(left, bottom - top)) // left
            drawRect(dimColor, Offset(right, top), Size(size.width - right, bottom - top)) // right

            // Crop rect border
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )

            // Corner handles
            val handleSize = 12.dp.toPx()
            val corners = listOf(
                Offset(left, top), Offset(right, top),
                Offset(left, bottom), Offset(right, bottom),
            )
            for (corner in corners) {
                drawCircle(Color.White, handleSize / 2, corner)
            }
        }
    }
}

@Composable
private fun RotateTab(viewModel: EditViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.rotateLeft() }) {
            Icon(Icons.Default.RotateLeft, "Rotate left", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { viewModel.rotateRight() }) {
            Icon(Icons.Default.RotateRight, "Rotate right", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { viewModel.flipH() }) {
            Icon(Icons.Default.Flip, "Flip horizontal", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { viewModel.flipV() }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Flip, "Flip vertical", modifier = Modifier.size(32.dp))
                Text("V", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CropTab(viewModel: EditViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = { viewModel.snapCropToRatio(1f) }, label = { Text("1:1") })
        AssistChip(onClick = { viewModel.snapCropToRatio(4f / 3f) }, label = { Text("4:3") })
        AssistChip(onClick = { viewModel.snapCropToRatio(16f / 9f) }, label = { Text("16:9") })
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { viewModel.applyCropFromRect() }) {
            Icon(Icons.Default.Check, "Apply crop", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AdjustTab(viewModel: EditViewModel) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
            Slider(
                value = brightness,
                onValueChange = {
                    brightness = it
                    viewModel.adjustColorsPreview(brightness, contrast, saturation)
                },
                valueRange = -1f..1f,
                onValueChangeFinished = { viewModel.commitColorAdjust(brightness, contrast, saturation) },
                modifier = Modifier.weight(0.7f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Contrast", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
            Slider(
                value = contrast,
                onValueChange = {
                    contrast = it
                    viewModel.adjustColorsPreview(brightness, contrast, saturation)
                },
                valueRange = -1f..1f,
                onValueChangeFinished = { viewModel.commitColorAdjust(brightness, contrast, saturation) },
                modifier = Modifier.weight(0.7f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saturation", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
            Slider(
                value = saturation,
                onValueChange = {
                    saturation = it
                    viewModel.adjustColorsPreview(brightness, contrast, saturation)
                },
                valueRange = 0f..2f,
                onValueChangeFinished = { viewModel.commitColorAdjust(brightness, contrast, saturation) },
                modifier = Modifier.weight(0.7f),
            )
        }
    }
}
