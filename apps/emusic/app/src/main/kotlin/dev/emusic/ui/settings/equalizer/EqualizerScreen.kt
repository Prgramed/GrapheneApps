package dev.emusic.ui.settings.equalizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.emusic.playback.EqualizerManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.equalizerState.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SavePresetDialog(
            onSave = { name ->
                viewModel.savePreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Equalizer") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(Icons.Default.Save, contentDescription = "Save preset")
                }
            },
        )

        if (!state.supported) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Equalizer not supported on this device",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Master enable
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable Equalizer", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Frequency response curve
            FrequencyResponseCurve(
                bandLevels = state.bandLevels,
                minLevel = state.minLevel,
                maxLevel = state.maxLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Band sliders
            if (state.bandLevels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    state.bandFrequencies.forEachIndexed { index, freqMHz ->
                        val label = formatFrequency(freqMHz)
                        val level = state.bandLevels.getOrElse(index) { 0 }
                        BandSlider(
                            label = label,
                            level = level,
                            minLevel = state.minLevel,
                            maxLevel = state.maxLevel,
                            onLevelChange = { viewModel.setBandLevel(index, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Bass boost
            Text("Bass Boost", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.bassBoostStrength.toFloat(),
                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
            )

            // Virtualizer
            Text("Virtualizer", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.virtualizerStrength.toFloat(),
                onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Preset selector
            Text("Presets", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Built-in presets
                EqualizerManager.BUILT_IN_PRESETS.keys.forEach { name ->
                    FilterChip(
                        selected = state.activePresetName == name,
                        onClick = { viewModel.applyBuiltInPreset(name) },
                        label = { Text(name) },
                    )
                }

                // Custom presets
                customPresets.forEach { preset ->
                    FilterChip(
                        selected = state.activePresetName == preset.name,
                        onClick = { viewModel.applyCustomPreset(preset) },
                        label = { Text(preset.name) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete ${preset.name}",
                                    modifier = Modifier.height(16.dp),
                                )
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "EQ may not apply on all devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BandSlider(
    label: String,
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            text = "${level / 100}dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Vertical slider via rotated horizontal slider
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            modifier = Modifier
                .height(140.dp)
                .width(48.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FrequencyResponseCurve(
    bandLevels: List<Int>,
    minLevel: Int,
    maxLevel: Int,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val range = (maxLevel - minLevel).toFloat().coerceAtLeast(1f)

    // Animate each band level
    val animatedLevels = bandLevels.mapIndexed { index, level ->
        animateFloatAsState(
            targetValue = level.toFloat(),
            animationSpec = spring(),
            label = "band_$index",
        ).value
    }

    Canvas(modifier = modifier) {
        if (animatedLevels.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val padding = 16f

        val points = animatedLevels.mapIndexed { index, level ->
            val x = padding + (w - 2 * padding) * index / (animatedLevels.size - 1).coerceAtLeast(1)
            val y = h - padding - (h - 2 * padding) * ((level - minLevel) / range)
            Offset(x, y)
        }

        // Draw curve with cubic bezier
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val cpx = (prev.x + curr.x) / 2
            path.cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3f),
        )

        // Draw dots at each band
        points.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = point,
            )
        }
    }
}

@Composable
private fun SavePresetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatFrequency(mHz: Int): String {
    val hz = mHz / 1000
    return if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
}
