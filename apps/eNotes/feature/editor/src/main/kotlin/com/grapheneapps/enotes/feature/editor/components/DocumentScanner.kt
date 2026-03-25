package com.grapheneapps.enotes.feature.editor.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches the camera to take a photo, saves to app-private storage.
 * The saved URI can be used as an image attachment in a note.
 */
@Composable
fun rememberDocumentScanLauncher(
    context: Context,
    onImageCaptured: (Uri) -> Unit,
    onCancelled: () -> Unit = {},
): () -> Unit {
    val tempFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
    val tempUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        tempFile,
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            onImageCaptured(tempUri)
        } else {
            onCancelled()
        }
    }

    return { launcher.launch(tempUri) }
}
