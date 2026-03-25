package com.grapheneapps.enotes.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var url by remember(uiState.webDavUrl) { mutableStateOf(uiState.webDavUrl) }
    var username by remember(uiState.webDavUsername) { mutableStateOf(uiState.webDavUsername) }
    var password by remember(uiState.webDavPassword) { mutableStateOf(uiState.webDavPassword) }
    val hasChanges = url != uiState.webDavUrl || username != uiState.webDavUsername || password != uiState.webDavPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime)
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(title = { Text("Settings") })

        Text(
            text = "WebDAV Sync",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("WebDAV URL") },
            placeholder = { Text("https://synology:5006/eNotes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (hasChanges) {
            TextButton(
                onClick = { viewModel.saveCredentials(url, username, password) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Save") }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.testConnection() },
            enabled = url.isNotBlank() && !uiState.isTesting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (uiState.isTesting) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Test Connection")
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { viewModel.syncNow() },
            enabled = uiState.webDavUrl.isNotBlank() && !uiState.isSyncing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (uiState.isSyncing) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text("Sync Now")
        }

        if (uiState.lastSyncTime > 0) {
            val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(uiState.lastSyncTime))
            Text(
                text = "Last sync: $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        uiState.message?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.contains("failed", ignoreCase = true) || msg.contains("Failed"))
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Sync interval
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Sync Interval",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val intervals = listOf(0 to "Manual only", 15 to "Every 15 min", 30 to "Every 30 min", 60 to "Every hour")
        var showIntervalMenu by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showIntervalMenu = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(intervals.find { it.first == uiState.syncIntervalMinutes }?.second ?: "Manual only")
        }
        androidx.compose.material3.DropdownMenu(
            expanded = showIntervalMenu,
            onDismissRequest = { showIntervalMenu = false },
        ) {
            intervals.forEach { (minutes, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        viewModel.setSyncInterval(minutes)
                        showIntervalMenu = false
                    },
                )
            }
        }

        // Joplin Import section
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Import from Joplin",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Text(
            text = "Enter the WebDAV URL Joplin uses to sync. On Synology: https://<NAS>:5006/<folder>",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        var joplinUrl by remember { mutableStateOf(uiState.joplinUrl) }
        var joplinUser by remember { mutableStateOf(uiState.joplinUsername) }
        var joplinPass by remember { mutableStateOf(uiState.joplinPassword) }

        OutlinedTextField(
            value = joplinUrl,
            onValueChange = { joplinUrl = it; viewModel.onJoplinUrlChanged(it) },
            label = { Text("Joplin WebDAV URL") },
            placeholder = { Text("https://nas:5006/joplin") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = joplinUser,
            onValueChange = { joplinUser = it; viewModel.onJoplinUsernameChanged(it) },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = joplinPass,
            onValueChange = { joplinPass = it; viewModel.onJoplinPasswordChanged(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        uiState.importProgress?.let { progress ->
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Button(
            onClick = { viewModel.importFromJoplin() },
            enabled = joplinUrl.isNotBlank() && !uiState.isImporting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (uiState.isImporting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Import from Joplin")
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}
