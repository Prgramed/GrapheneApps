package com.prgramed.econtacts.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onCardDavClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.exportUri) {
        uiState.exportUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share contacts"))
            viewModel.onExportHandled()
        }
    }

    if (uiState.isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting contacts") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Preparing vCard file...")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelExport() }) {
                    Text("Cancel")
                }
            },
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importContacts(it)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Duplicates
            ListItem(
                headlineContent = { Text("Find duplicates") },
                supportingContent = { Text("Detect and merge duplicate contacts") },
                leadingContent = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                },
                modifier = Modifier.clickable { onDuplicatesClick() },
            )
            HorizontalDivider()

            // CardDAV Sync
            ListItem(
                headlineContent = { Text("CardDAV sync") },
                supportingContent = { Text("Sync contacts with a server") },
                leadingContent = {
                    Icon(Icons.Default.Cloud, contentDescription = null)
                },
                modifier = Modifier.clickable { onCardDavClick() },
            )
            HorizontalDivider()

            // Import
            ListItem(
                headlineContent = { Text("Import contacts") },
                supportingContent = { Text("Import from vCard file") },
                leadingContent = {
                    Icon(Icons.Default.Download, contentDescription = null)
                },
                modifier = Modifier.clickable { importLauncher.launch("text/x-vcard") },
            )
            HorizontalDivider()

            // Export
            ListItem(
                headlineContent = { Text("Export all contacts") },
                supportingContent = { Text("Export to vCard file") },
                leadingContent = {
                    Icon(Icons.Default.Upload, contentDescription = null)
                },
                modifier = Modifier.clickable { viewModel.exportAllContacts() },
            )
            HorizontalDivider()

            // Import status
            if (uiState.importResult != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            "Imported ${uiState.importResult} contacts",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ContactSupport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}
