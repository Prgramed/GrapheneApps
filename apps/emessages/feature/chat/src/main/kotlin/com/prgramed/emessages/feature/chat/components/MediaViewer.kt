package com.prgramed.emessages.feature.chat.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.prgramed.emessages.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaViewer(
    attachment: Attachment,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(isAttachmentSaved(context, attachment)) }
    var saving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Image with pinch-to-zoom + tap to dismiss
        AsyncImage(
            model = attachment.uri,
            contentDescription = "Full image",
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (scale <= 1f) onDismiss()
                        },
                    )
                },
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Back button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        // Save / Saved indicator
        if (!saved) {
            IconButton(
                onClick = {
                    if (!saving) {
                        saving = true
                        scope.launch {
                            val success = saveAttachmentToGallery(context, attachment)
                            saving = false
                            if (success) saved = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Save to gallery",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Saved",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun stableFileName(attachment: Attachment): String {
    // Derive a stable name from the URI (contains MMS part ID)
    val partId = Uri.parse(attachment.uri).lastPathSegment ?: "unknown"
    val extension = when {
        attachment.mimeType.contains("jpeg") || attachment.mimeType.contains("jpg") -> "jpg"
        attachment.mimeType.contains("png") -> "png"
        attachment.mimeType.contains("gif") -> "gif"
        attachment.mimeType.contains("webp") -> "webp"
        attachment.mimeType.startsWith("video/") -> "mp4"
        else -> "jpg"
    }
    return "MMS_$partId.$extension"
}

fun isAttachmentSaved(context: Context, attachment: Attachment): Boolean {
    val fileName = stableFileName(attachment)
    val collection = if (attachment.mimeType.startsWith("video/")) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    return try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null,
        )?.use { it.count > 0 } ?: false
    } catch (_: Exception) {
        false
    }
}

suspend fun saveAttachmentToGallery(context: Context, attachment: Attachment): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // Check if already saved
            if (isAttachmentSaved(context, attachment)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Already saved", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }

            val uri = Uri.parse(attachment.uri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Cannot read attachment", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }

            val mimeType = attachment.mimeType
            val fileName = stableFileName(attachment)

            val collection = if (mimeType.startsWith("video/")) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val relativePath = if (mimeType.startsWith("video/")) {
                Environment.DIRECTORY_MOVIES
            } else {
                Environment.DIRECTORY_PICTURES
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val outputUri = context.contentResolver.insert(collection, values)
            if (outputUri != null) {
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }
            inputStream.close()
            false
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}
