package dev.emusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onEqualizerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val forceOfflineMode by viewModel.forceOfflineMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Server Settings",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Connect to your Navidrome server via Tailscale using your home IP address.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { viewModel.serverUrl.value = it },
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.x:4533") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { viewModel.username.value = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.password.value = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.testConnection() },
            enabled = connectionStatus != ConnectionStatus.TESTING &&
                serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connectionStatus == ConnectionStatus.TESTING) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Test Connection")
            }
        }

        when (connectionStatus) {
            ConnectionStatus.SUCCESS -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connection successful",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ConnectionStatus.ERROR -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Connection failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> {}
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.save() },
            enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
                syncProgress == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & Sync Library")
        }

        // Sync progress
        if (syncProgress != null) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = syncProgress?.stage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.cancelSync() }) {
                    Text("Cancel")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.forceFullSync() },
            enabled = syncProgress == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Force Full Library Re-sync")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Downloads & Offline",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download on WiFi only", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Prevent downloads on mobile data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = wifiOnlyDownloads,
                onCheckedChange = { viewModel.updateWifiOnlyDownloads(it) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Force offline mode", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Only play downloaded content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = forceOfflineMode,
                onCheckedChange = { viewModel.updateForceOfflineMode(it) },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(12.dp))

        val scrobblingEnabled by viewModel.scrobblingEnabled.collectAsStateWithLifecycle()
        val headsUpEnabled by viewModel.headsUpEnabled.collectAsStateWithLifecycle()

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable scrobbling", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Report plays to Navidrome",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = scrobblingEnabled,
                onCheckedChange = { viewModel.updateScrobblingEnabled(it) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Track change notifications", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Brief heads-up on skip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = headsUpEnabled,
                onCheckedChange = { viewModel.updateHeadsUpEnabled(it) },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Streaming Quality",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Quality adjusts automatically based on battery level. Set a fixed bitrate to override.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        val bitrateOptions = listOf(0 to "Auto (battery-aware)", 96 to "96 kbps", 128 to "128 kbps", 192 to "192 kbps", 320 to "320 kbps")
        val currentBitrate by viewModel.maxBitrate.collectAsStateWithLifecycle()
        bitrateOptions.forEach { (value, label) ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateMaxBitrate(value) }
                    .padding(vertical = 8.dp),
            ) {
                androidx.compose.material3.RadioButton(
                    selected = currentBitrate == value,
                    onClick = { viewModel.updateMaxBitrate(value) },
                )
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Volume Normalisation",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        val rgMode by viewModel.replayGainMode.collectAsStateWithLifecycle()
        val preAmp by viewModel.preAmpDb.collectAsStateWithLifecycle()

        val rgModes = listOf(
            0 to "Track Gain",
            1 to "Album Gain",
            2 to "Auto",
            3 to "Off",
        )
        rgModes.forEach { (value, label) ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateReplayGainMode(value) }
                    .padding(vertical = 8.dp),
            ) {
                androidx.compose.material3.RadioButton(
                    selected = rgMode == value,
                    onClick = { viewModel.updateReplayGainMode(value) },
                )
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (rgMode != 3) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pre-amp: ${"%.1f".format(preAmp)} dB",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.Slider(
                value = preAmp,
                onValueChange = { viewModel.updatePreAmpDb(it) },
                valueRange = -6f..6f,
                steps = 11,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Equalizer
        Text(
            text = "Equalizer",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEqualizerClick)
                .padding(vertical = 12.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Crossfade
        val crossfadeMs by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
        val crossfadeSec = crossfadeMs / 1000f
        Text(
            text = "Crossfade: ${"%.0f".format(crossfadeSec)}s${if (crossfadeSec == 0f) " (Off)" else ""}",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.material3.Slider(
            value = crossfadeSec,
            onValueChange = { viewModel.updateCrossfadeDuration((it * 1000).toInt()) },
            valueRange = 0f..12f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
        )

        // Gapless
        val gapless by viewModel.gaplessPlayback.collectAsStateWithLifecycle()
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Gapless playback", modifier = Modifier.weight(1f))
            Switch(
                checked = gapless,
                onCheckedChange = { viewModel.updateGaplessPlayback(it) },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Appearance",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
        val themeOptions = listOf(0 to "System default", 1 to "Light", 2 to "Dark")
        themeOptions.forEach { (value, label) ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateThemeMode(value) }
                    .padding(vertical = 8.dp),
            ) {
                androidx.compose.material3.RadioButton(
                    selected = currentTheme == value,
                    onClick = { viewModel.updateThemeMode(value) },
                )
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "About",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "eMusic v1.0.0",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Music streaming for Navidrome via Subsonic API",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
    }
}
