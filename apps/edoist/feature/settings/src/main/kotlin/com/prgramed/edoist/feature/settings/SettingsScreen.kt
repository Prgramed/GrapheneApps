package com.prgramed.edoist.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.edoist.feature.settings.components.WebDavConfigDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    var showWebDavDialog by remember { mutableStateOf(false) }

    // File picker for Todoist ZIP import
    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromTodoist(it) }
    }

    // Import result dialog
    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportResult() },
            title = { Text("Import complete") },
            text = {
                Text(
                    buildString {
                        append("${result.projectsImported} projects\n")
                        append("${result.tasksImported} tasks\n")
                        append("${result.sectionsImported} sections")
                        if (result.errors.isNotEmpty()) {
                            append("\n\nErrors:\n")
                            result.errors.forEach { append("- $it\n") }
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportResult() }) {
                    Text("OK")
                }
            },
        )
    }

    if (showWebDavDialog) {
        WebDavConfigDialog(
            currentUrl = uiState.webDavUrl,
            currentUsername = uiState.webDavUsername,
            onSave = { url, username, password ->
                viewModel.saveWebDavConfig(url, username, password)
                showWebDavDialog = false
            },
            onDismiss = { showWebDavDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Sync section ────────────────────────────────────────────
            SectionHeader(title = "Sync")

            ListItem(
                headlineContent = { Text("Enable sync") },
                supportingContent = { Text("Sync tasks via WebDAV") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = uiState.syncEnabled,
                        onCheckedChange = viewModel::onSyncToggled,
                    )
                },
            )

            ListItem(
                headlineContent = { Text("WebDAV server") },
                supportingContent = {
                    Text(
                        text = uiState.webDavUrl.ifBlank { "Not configured" },
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable { showWebDavDialog = true },
            )

            ListItem(
                headlineContent = { Text("Sync interval") },
                supportingContent = {
                    Text(
                        text = SettingsViewModel.formatSyncInterval(uiState.syncIntervalMinutes),
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.onSyncIntervalChanged(
                        SettingsViewModel.nextSyncInterval(uiState.syncIntervalMinutes),
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Sync now") },
                supportingContent = {
                    if (uiState.isSyncing) {
                        Text("Syncing...")
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.clickable(enabled = !uiState.isSyncing) {
                    viewModel.syncNow()
                },
            )

            if (uiState.lastSyncMillis != null || uiState.lastSyncStatus != null) {
                ListItem(
                    headlineContent = { Text("Last sync") },
                    supportingContent = {
                        val timeText = uiState.lastSyncMillis?.let { millis ->
                            formatRelativeTime(millis)
                        } ?: "Never"
                        val statusText = uiState.lastSyncStatus?.let { " ($it)" } ?: ""
                        Text(text = "$timeText$statusText")
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Display section ─────────────────────────────────────────
            SectionHeader(title = "Display")

            ListItem(
                headlineContent = { Text("Show completed tasks") },
                trailingContent = {
                    Switch(
                        checked = uiState.showCompletedTasks,
                        onCheckedChange = viewModel::onShowCompletedToggled,
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Dynamic colors") },
                supportingContent = { Text("Material You theming") },
                trailingContent = {
                    Switch(
                        checked = uiState.dynamicColor,
                        onCheckedChange = viewModel::onDynamicColorToggled,
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Import section ──────────────────────────────────────────
            SectionHeader(title = "Import")

            ListItem(
                headlineContent = { Text("Import from Todoist") },
                supportingContent = { Text("Import a Todoist backup ZIP file") },
                leadingContent = {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About section ───────────────────────────────────────────
            SectionHeader(title = "About")

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun formatRelativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    val seconds = diff / 1_000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }
}
