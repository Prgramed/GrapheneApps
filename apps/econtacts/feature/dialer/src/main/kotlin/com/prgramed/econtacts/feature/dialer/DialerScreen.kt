package com.prgramed.econtacts.feature.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class DialButton(
    val digit: String,
    val letters: String,
)

private val dialButtons = listOf(
    listOf(DialButton("1", ""), DialButton("2", "ABC"), DialButton("3", "DEF")),
    listOf(DialButton("4", "GHI"), DialButton("5", "JKL"), DialButton("6", "MNO")),
    listOf(DialButton("7", "PQRS"), DialButton("8", "TUV"), DialButton("9", "WXYZ")),
    listOf(DialButton("*", ""), DialButton("0", "+"), DialButton("#", "")),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    onBack: () -> Unit = {},
    onCallInitiated: () -> Unit = {},
    onCreateContact: (String) -> Unit = {},
    initialNumber: String? = null,
    modifier: Modifier = Modifier,
    viewModel: DialerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    // Handle call placed event
    LaunchedEffect(uiState.callPlaced) {
        if (uiState.callPlaced) {
            onCallInitiated()
            viewModel.onCallPlacedHandled()
        }
    }

    // Pre-fill number from intent
    LaunchedEffect(initialNumber) {
        if (!initialNumber.isNullOrBlank()) {
            viewModel.setNumber(initialNumber)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.CALL_PHONE] == true) {
            viewModel.initiateCall()
        }
    }

    // SIM picker dialog
    if (uiState.showSimPicker) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSimPicker() },
            title = { Text("Select SIM") },
            text = {
                Column {
                    uiState.availableSims.forEachIndexed { index, sim ->
                        TextButton(
                            onClick = { viewModel.selectSim(sim) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = "SIM ${index + 1}: ${sim.displayName}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (sim.carrierName.isNotBlank()) {
                                    Text(
                                        text = sim.carrierName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (index < uiState.availableSims.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSimPicker() }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Number display with paste + backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Paste button
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            IconButton(onClick = {
                val clip = clipboardManager.getText()?.text ?: return@IconButton
                viewModel.setNumber(clip)
            }) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = uiState.currentNumber.ifEmpty { " " },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (uiState.currentNumber.isNotEmpty()) {
                IconButton(onClick = { viewModel.onBackspace() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                    )
                }
            }
        }

        // Create contact button (when number entered and no match)
        if (uiState.currentNumber.isNotEmpty() && uiState.matchedContactName == null) {
            TextButton(onClick = { onCreateContact(uiState.currentNumber) }) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Text("Create contact")
            }
        }

        // Matched contact name
        if (uiState.matchedContactName != null) {
            Text(
                text = uiState.matchedContactName!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dial pad
        dialButtons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { button ->
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .combinedClickable(
                                onClick = {
                                    viewModel.onDigitPressed(button.digit)
                                    val toneType = dialToneType(button.digit)
                                    if (toneType >= 0) toneGenerator?.startTone(toneType, 150)
                                },
                                onLongClick = {
                                    if (button.digit == "0") {
                                        viewModel.onDigitPressed("+")
                                    }
                                },
                            ),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = button.digit,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (button.letters.isNotEmpty()) {
                                Text(
                                    text = button.letters,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Call button
        FloatingActionButton(
            onClick = {
                if (uiState.currentNumber.isNotBlank()) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CALL_PHONE,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        viewModel.initiateCall()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE,
                            ),
                        )
                    }
                }
            },
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Default.Call, contentDescription = "Call")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun dialToneType(digit: String): Int = when (digit) {
    "0" -> ToneGenerator.TONE_DTMF_0
    "1" -> ToneGenerator.TONE_DTMF_1
    "2" -> ToneGenerator.TONE_DTMF_2
    "3" -> ToneGenerator.TONE_DTMF_3
    "4" -> ToneGenerator.TONE_DTMF_4
    "5" -> ToneGenerator.TONE_DTMF_5
    "6" -> ToneGenerator.TONE_DTMF_6
    "7" -> ToneGenerator.TONE_DTMF_7
    "8" -> ToneGenerator.TONE_DTMF_8
    "9" -> ToneGenerator.TONE_DTMF_9
    "*" -> ToneGenerator.TONE_DTMF_S
    "#" -> ToneGenerator.TONE_DTMF_P
    else -> -1
}
