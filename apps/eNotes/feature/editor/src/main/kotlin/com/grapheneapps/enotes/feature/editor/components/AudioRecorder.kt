package com.grapheneapps.enotes.feature.editor.components

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecorderSheet(
    context: Context,
    onRecordingComplete: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    val outputFile = remember { File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) { }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Voice Memo",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(16.dp))

            // Timer
            val seconds = (elapsedMs / 1000).toInt()
            val minutes = seconds / 60
            val secs = seconds % 60
            Text(
                text = "%d:%02d".format(minutes, secs),
                style = MaterialTheme.typography.displaySmall,
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                if (!isRecording) {
                    IconButton(onClick = {
                        try {
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            mr.setOutputFile(outputFile.absolutePath)
                            mr.prepare()
                            mr.start()
                            recorder = mr
                            isRecording = true
                            elapsedMs = 0

                            scope.launch {
                                while (isRecording) {
                                    delay(100)
                                    elapsedMs += 100
                                }
                            }
                        } catch (_: Exception) { }
                    }) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = "Record",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                } else {
                    IconButton(onClick = {
                        isRecording = false
                        try {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            onRecordingComplete(outputFile)
                            onDismiss()
                        } catch (_: Exception) { }
                    }) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
