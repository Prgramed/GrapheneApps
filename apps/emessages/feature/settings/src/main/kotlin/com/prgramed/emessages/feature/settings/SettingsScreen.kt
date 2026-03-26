package com.prgramed.emessages.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBlockedNumbersClick: () -> Unit = {},
    onRecentlyDeletedClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Local state for WebDAV fields so keyboard doesn't save on every keystroke
    var webDavUrl by remember(uiState.webDavUrl) { mutableStateOf(uiState.webDavUrl) }
    var webDavUsername by remember(uiState.webDavUsername) { mutableStateOf(uiState.webDavUsername) }
    var webDavPassword by remember(uiState.webDavPassword) { mutableStateOf(uiState.webDavPassword) }
    val hasUnsavedChanges = webDavUrl != uiState.webDavUrl ||
        webDavUsername != uiState.webDavUsername ||
        webDavPassword != uiState.webDavPassword

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Notifications") },
                supportingContent = { Text("Show notifications for new messages") },
                trailingContent = {
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = viewModel::onNotificationsToggled,
                    )
                },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Delivery reports") },
                supportingContent = { Text("Request delivery confirmation for sent messages") },
                trailingContent = {
                    Switch(
                        checked = uiState.deliveryReportsEnabled,
                        onCheckedChange = viewModel::onDeliveryReportsToggled,
                    )
                },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Blocked numbers") },
                supportingContent = { Text("Manage blocked phone numbers") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onBlockedNumbersClick),
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Recently deleted") },
                supportingContent = { Text("Conversations deleted in the last 30 days") },
                leadingContent = {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onRecentlyDeletedClick),
            )
            HorizontalDivider()

            // Backup section
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = webDavUrl,
                onValueChange = { webDavUrl = it },
                label = { Text("WebDAV URL") },
                placeholder = { Text("https://synology:5006/eMessages") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            OutlinedTextField(
                value = webDavUsername,
                onValueChange = { webDavUsername = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            OutlinedTextField(
                value = webDavPassword,
                onValueChange = { webDavPassword = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (hasUnsavedChanges) {
                TextButton(
                    onClick = {
                        viewModel.onWebDavUrlChanged(webDavUrl.trim())
                        viewModel.onWebDavUsernameChanged(webDavUsername.trim())
                        viewModel.onWebDavPasswordChanged(webDavPassword.trim())
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("Save")
                }
            }

            if (uiState.lastBackupTime > 0) {
                val dateStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                    .format(Date(uiState.lastBackupTime))
                Text(
                    text = "Last backup: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            uiState.backupMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.contains("failed", ignoreCase = true))
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::backupNow,
                enabled = uiState.webDavUrl.isNotBlank() && !uiState.isBackingUp && !uiState.isRestoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (uiState.isBackingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Backup Now")
                }
            }

            Spacer(Modifier.height(4.dp))

            var showRestoreConfirm by remember { mutableStateOf(false) }
            if (showRestoreConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRestoreConfirm = false },
                    title = { Text("Restore messages?") },
                    text = { Text("This will merge the backup into your current messages. Duplicate messages will be skipped.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showRestoreConfirm = false
                            viewModel.restoreNow()
                        }) { Text("Restore") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") }
                    },
                )
            }
            OutlinedButton(
                onClick = { showRestoreConfirm = true },
                enabled = uiState.webDavUrl.isNotBlank() && !uiState.isRestoring && !uiState.isBackingUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Restore from Backup")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Force restore buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.forceRestoreSms() },
                    enabled = uiState.webDavUrl.isNotBlank() && !uiState.isRestoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Force Restore SMS", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.forceRestoreMms() },
                    enabled = uiState.webDavUrl.isNotBlank() && !uiState.isRestoring,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Force Restore MMS", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.deleteOldSms() },
                enabled = !uiState.isRestoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete Old SMS (before today)", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
