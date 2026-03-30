package dev.egallery.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTrashClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val connectionTest by viewModel.connectionTest.collectAsState()
    val wifiOnly by viewModel.wifiOnlyUpload.collectAsState()
    val autoEvict by viewModel.autoEvictEnabled.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val pendingUploads by viewModel.pendingUploadCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val localStorageBytes by viewModel.localStorageBytes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Connection
            SectionHeader("Immich Server")
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.serverUrl.value = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.x.x:2283") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.apiKey.value = it },
                label = { Text("API Key") },
                placeholder = { Text("Paste your Immich API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.testConnection() }) {
                    Text("Test Connection")
                }
                Spacer(Modifier.weight(1f))
                when (val state = connectionTest) {
                    is ConnectionTestState.Testing -> Text("Testing…", style = MaterialTheme.typography.bodySmall)
                    is ConnectionTestState.Success -> Text(
                        if (state.version.isNotBlank()) "Connected (v${state.version})" else "Connected",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is ConnectionTestState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> {}
                }
            }

            SectionDivider()

            // Storage
            SectionHeader("Storage")
            SettingsToggle("Auto-evict old local files", autoEvict) { viewModel.setAutoEvictEnabled(it) }
            Text(
                text = "Photos older than 1 year are automatically removed from this device. Thumbnails remain for browsing. Originals stay safe on server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            val autoDeleteCovers by viewModel.autoDeleteCovers.collectAsState()
            SettingsToggle("Auto-delete Video Boost covers", autoDeleteCovers) { viewModel.setAutoDeleteCovers(it) }
            Text(
                text = "Delete low-res cover videos after 30 days if the full Video Boost video exists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Local storage used: ${formatBytes(localStorageBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            SectionDivider()

            // Sync
            SectionHeader("Sync")
            if (lastSyncAt > 0) {
                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
                Text(
                    text = "Last sync: ${dateFormat.format(Date(lastSyncAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.quickSync() },
                    enabled = !isSyncing,
                ) {
                    Text("Quick Sync")
                }
                OutlinedButton(
                    onClick = { viewModel.forceFullResync() },
                    enabled = !isSyncing,
                ) {
                    Text("Force Full Sync")
                }
                if (isSyncing) {
                    OutlinedButton(onClick = { viewModel.cancelSync() }) {
                        Text("Cancel")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Status: $syncStatus",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    syncStatus.contains("error", ignoreCase = true) || syncStatus.contains("failed", ignoreCase = true) || syncStatus.contains("Cancelled") -> MaterialTheme.colorScheme.error
                    syncStatus.contains("sync", ignoreCase = true) || syncStatus.contains("Fetching") -> MaterialTheme.colorScheme.tertiary
                    syncStatus.startsWith("Done") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            SectionDivider()

            // Uploads
            SectionHeader("Uploads")
            val autoUpload by viewModel.autoUploadEnabled.collectAsState()
            SettingsToggle("Auto-upload new photos", autoUpload) { viewModel.setAutoUploadEnabled(it) }
            Text(
                text = "Automatically upload photos taken with the camera to Immich",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            SettingsToggle("WiFi-only uploads", wifiOnly) { viewModel.setWifiOnlyUpload(it) }
            Spacer(Modifier.height(8.dp))

            // Upload status
            val uploadProgress by viewModel.uploadProgress.collectAsState()
            val isUploading by viewModel.isUploading.collectAsState()
            if (uploadProgress != "Idle") {
                Text(
                    text = "Status: $uploadProgress",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        uploadProgress.contains("failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
                        isUploading -> MaterialTheme.colorScheme.tertiary
                        uploadProgress.startsWith("Done") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Retry button — always visible (finds orphaned UPLOAD_FAILED in media DB too)
            OutlinedButton(onClick = { viewModel.retryFailedUploads() }) {
                Text("Retry Failed Uploads")
            }
            Spacer(Modifier.height(8.dp))

            // Upload queue status
            val failedUploads by viewModel.failedUploadCount.collectAsState()
            val totalUploads by viewModel.totalUploadCount.collectAsState()
            if (totalUploads == 0) {
                Text(
                    text = "No uploads in queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (pendingUploads > 0) {
                    Text(
                        text = "$pendingUploads pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (failedUploads > 0) {
                    Text(
                        text = "$failedUploads failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.retryFailedUploads() }) {
                        Text("Retry Failed")
                    }
                    if (pendingUploads > 0) {
                        OutlinedButton(onClick = { viewModel.triggerUpload() }) {
                            Text("Upload Now")
                        }
                    }
                }
            }

            SectionDivider()

            // Trash
            OutlinedButton(onClick = onTrashClick, modifier = Modifier.fillMaxWidth()) {
                Text("Trash — view deleted photos")
            }

            SectionDivider()

            // About
            SectionHeader("About")
            Text(
                text = "eGallery v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your photos live on your NAS — this app never sends data anywhere else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalDropdown(
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit,
) {
    val options = listOf(0 to "Manual", 1 to "Every hour", 2 to "Every 2 hours", 6 to "Every 6 hours")
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == currentInterval }?.second ?: "Every hour"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync interval") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (hours, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onIntervalSelected(hours)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
