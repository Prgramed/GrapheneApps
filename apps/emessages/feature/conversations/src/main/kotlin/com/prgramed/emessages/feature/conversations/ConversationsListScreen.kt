package com.prgramed.emessages.feature.conversations

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.prgramed.emessages.domain.model.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    onConversationClick: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSearchVisible by remember { mutableStateOf(false) }

    // Refresh conversations on every resume (catches messages received while app was backgrounded)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val smsGranted = results[Manifest.permission.READ_SMS] == true
        viewModel.onPermissionResult(smsGranted)
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
        )
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchVisible) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchChanged(it) },
                            placeholder = { Text("Search messages") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.onSearchChanged("")
                                    isSearchVisible = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            },
                        )
                    } else {
                        Text("Messages")
                    }
                },
                actions = {
                    if (!isSearchVisible) {
                        IconButton(onClick = { isSearchVisible = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onNewMessage) {
                            Icon(Icons.Default.Edit, contentDescription = "New message")
                        }
                    }
                },
            )
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
                uiState.conversations.isEmpty() -> {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "No results" else "No conversations",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.conversations,
                            key = { it.threadId },
                        ) { conversation ->
                            var showDeleteDialog by remember { mutableStateOf(false) }
                            val isUnread = conversation.unreadCount > 0
                            val currentIsUnread by rememberUpdatedState(isUnread)
                            var swipeHandled by remember { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            swipeHandled = true
                                            if (currentIsUnread) viewModel.markThreadAsRead(conversation.threadId)
                                            else viewModel.markThreadAsUnread(conversation.threadId)
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            swipeHandled = true
                                            showDeleteDialog = true
                                        }
                                        else -> {}
                                    }
                                    false
                                },
                            )

                            // Backup: if confirmValueChange doesn't fire reliably
                            LaunchedEffect(dismissState.currentValue) {
                                when (dismissState.currentValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        if (!swipeHandled) {
                                            if (currentIsUnread) viewModel.markThreadAsRead(conversation.threadId)
                                            else viewModel.markThreadAsUnread(conversation.threadId)
                                        }
                                        swipeHandled = false
                                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        if (!swipeHandled) showDeleteDialog = true
                                        swipeHandled = false
                                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                    SwipeToDismissBoxValue.Settled -> swipeHandled = false
                                }
                            }
                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text("Delete conversation?") },
                                    text = { Text("This conversation will be moved to recently deleted and removed after 30 days.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showDeleteDialog = false
                                            viewModel.deleteConversation(conversation.threadId)
                                        }) { Text("Delete") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showDeleteDialog = false
                                        }) { Text("Cancel") }
                                    },
                                )
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val isStartToEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isStartToEnd) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                            )
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            text = if (isStartToEnd) {
                                                if (isUnread) "Read" else "Unread"
                                            } else "Delete",
                                            color = if (isStartToEnd) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onError,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                },
                            ) {
                                val simLabel = if (uiState.isDualSim) {
                                    val subId = conversation.lastMessageSubscriptionId
                                    uiState.availableSims.find { it.subscriptionId == subId }
                                        ?.displayName
                                } else null
                                ConversationRow(
                                    conversation = conversation,
                                    simLabel = simLabel,
                                    onClick = { onConversationClick(conversation.threadId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    simLabel: String? = null,
) {
    val recipient = conversation.recipients.firstOrNull()
    val displayName = recipient?.let { it.contactName ?: it.address } ?: "Unknown"
    val photoUri = recipient?.contactPhotoUri
    val initials = displayName.take(1).uppercase()
    val isUnread = conversation.unreadCount > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar with contact photo
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Name + preview
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val draftText = com.prgramed.emessages.domain.model.DraftStore.get(conversation.threadId)
            Text(
                text = if (draftText != null) "Draft: $draftText" else conversation.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = if (draftText != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Timestamp + unread dot
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (simLabel != null) {
                Text(
                    text = simLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = formatRelativeTimestamp(conversation.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = isUnread,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun formatRelativeTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> "Now"
        diff < DateUtils.HOUR_IN_MILLIS -> "${diff / DateUtils.MINUTE_IN_MILLIS}m"
        diff < DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.HOUR_IN_MILLIS}h"
        diff < 7 * DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.DAY_IN_MILLIS}d"
        else -> DateUtils.formatDateTime(
            null,
            timestamp,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
}
