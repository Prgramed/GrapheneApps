package com.prgramed.econtacts.feature.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.econtacts.domain.model.CallType
import com.prgramed.econtacts.domain.model.RecentCall
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    onContactClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var simPickerNumber by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }

    if (simPickerNumber != null) {
        SimPickerDialog(
            sims = availableSims,
            onSimSelected = { sim ->
                placeCallWithSim(context, simPickerNumber!!, sim.phoneAccountHandle)
                simPickerNumber = null
            },
            onDismiss = { simPickerNumber = null },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Recent calls") })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Error",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                uiState.calls.isEmpty() -> {
                    Text(
                        text = "No recent calls",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            uiState.calls,
                            key = { it.id },
                        ) { call ->
                            CallRow(
                                call = call,
                                onClick = {
                                    if (call.number.isNotBlank()) {
                                        val cid = call.contactId
                                        if (cid != null) {
                                            onContactClick(cid)
                                        } else {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.number}")),
                                            )
                                        }
                                    }
                                },
                                onCallClick = {
                                    if (call.number.isNotBlank()) {
                                        placeCallDefault(context, call.number)
                                    }
                                },
                                onCallLongClick = {
                                    if (call.number.isNotBlank()) {
                                        val sims = loadAvailableSims(context)
                                        if (sims.size >= 2) {
                                            availableSims = sims
                                            simPickerNumber = call.number
                                        } else {
                                            placeCallDefault(context, call.number)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    call: RecentCall,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onCallLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (call.type) {
        CallType.INCOMING -> Icons.Default.CallReceived
        CallType.OUTGOING -> Icons.Default.CallMade
        CallType.MISSED -> Icons.Default.CallMissed
    }
    val iconTint = when (call.type) {
        CallType.MISSED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeText = remember(call.timestamp) {
        java.time.Instant.ofEpochMilli(call.timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(callDateFormat)
    }

    val displayName = call.name?.takeIf { it.isNotBlank() } ?: call.number.ifBlank { "Unknown" }

    ListItem(
        headlineContent = {
            Text(
                text = displayName,
                color = if (call.type == CallType.MISSED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            // Show number below name if name was resolved, or show time
            val supporting = if (!call.name.isNullOrBlank() && call.number.isNotBlank()) {
                "${call.number} · $timeText"
            } else {
                timeText
            }
            Text(supporting)
        },
        leadingContent = {
            Icon(icon, contentDescription = call.type.name, tint = iconTint)
        },
        trailingContent = {
            IconButton(onClick = onCallClick) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onCallLongClick,
        ),
    )
}

private val callDateFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
