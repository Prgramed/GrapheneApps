package com.prgramed.emessages.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.prgramed.emessages.domain.model.Message
import com.prgramed.emessages.domain.model.MessageType
import android.content.Context
import com.prgramed.emessages.feature.chat.components.ChatBubble
import com.prgramed.emessages.feature.chat.components.DateSeparator
import com.prgramed.emessages.feature.chat.components.MediaViewer
import com.prgramed.emessages.feature.chat.components.saveAttachmentToGallery
import com.prgramed.emessages.feature.chat.components.MessageInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onForwardMessage: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val linkPreviews = viewModel.linkPreviews
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var viewingAttachment by remember { mutableStateOf<com.prgramed.emessages.domain.model.Attachment?>(null) }

    // Photo picker
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.onAttachmentSelected(it.toString()) }
    }

    // Auto-scroll when a new message appears (sent or received) and user is near bottom
    val newestMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(newestMessageId) {
        if (newestMessageId != null && listState.firstVisibleItemIndex < 5) {
            listState.animateScrollToItem(0)
        }
    }

    // Always scroll after sending — with delay to let ContentObserver pick up the new message
    LaunchedEffect(uiState.scrollToBottomEvent) {
        if (uiState.scrollToBottomEvent > 0) {
            delay(600)
            if (uiState.messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Load more when scrolling near the oldest messages (reverseLayout: last visible = oldest)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // Message context menu bottom sheet
    if (uiState.selectedMessage != null) {
        val sheetState = rememberModalBottomSheetState()
        val selectedMsg = uiState.selectedMessage!!

        ModalBottomSheet(
            onDismissRequest = { viewModel.onMessageSelected(null) },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Reply") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        viewModel.onReplyToMessage(selectedMsg)
                        viewModel.onMessageSelected(null)
                    },
                )
                if (selectedMsg.body.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Copy") },
                        leadingContent = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            val clip = android.content.ClipData.newPlainText("message", selectedMsg.body)
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                            viewModel.onMessageSelected(null)
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Forward") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            viewModel.onMessageSelected(null)
                            onForwardMessage(selectedMsg.body)
                        },
                    )
                }
                ListItem(
                    headlineContent = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable {
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && uiState.selectedMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                viewModel.onMessageSelected(null)
            },
            title = { Text("Delete message?") },
            text = { Text("This message will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(uiState.selectedMessage!!)
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onMessageSelected(null)
                }) { Text("Cancel") }
            },
        )
    }

    // Block number dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block this number?") },
            text = {
                Text("Messages from ${uiState.recipientAddress} will be silently blocked.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val values = android.content.ContentValues().apply {
                                    put(
                                        android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                                        uiState.recipientAddress,
                                    )
                                }
                                context.contentResolver.insert(
                                    android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                    values,
                                )
                            } catch (_: Exception) {
                            }
                        }
                        showBlockDialog = false
                    }
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Send error snackbar
    uiState.sendError?.let { error ->
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.dismissSendError() }) { Text("OK") }
            },
        ) { Text(error) }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Contact photo
                        if (uiState.recipientPhotoUri != null) {
                            AsyncImage(
                                model = uiState.recipientPhotoUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (uiState.recipientName.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = uiState.recipientName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            text = uiState.recipientName.ifBlank { "Chat" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Call button
                    if (uiState.recipientAddress.isNotBlank()) {
                        IconButton(onClick = {
                            val callIntent = android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:${uiState.recipientAddress}"),
                            )
                            context.startActivity(callIntent)
                        }) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call",
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Block number") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showBlockDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val groupedMessages = remember(uiState.messages) {
                    groupMessagesWithDates(uiState.messages.reversed())
                }

                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    items(
                        items = groupedMessages,
                        key = { item ->
                            when (item) {
                                is ChatItem.MessageItem -> "msg_${item.message.id}"
                                is ChatItem.DateHeader -> "date_${item.dateMillis}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ChatItem.MessageItem -> {
                                val simLabel = if (uiState.isDualSim) {
                                    val subId = item.message.subscriptionId
                                    uiState.availableSims.find { it.subscriptionId == subId }
                                        ?.displayName
                                } else null
                                ChatBubble(
                                    modifier = Modifier.animateItem(),
                                    message = item.message,
                                    isSent = item.message.type == MessageType.SENT,
                                    isLastInGroup = item.isLastInGroup,
                                    simLabel = simLabel,
                                    linkPreview = linkPreviews[item.message.id],
                                    onLongPress = { viewModel.onMessageSelected(it) },
                                    onAttachmentClick = { attachment ->
                                        if (attachment.mimeType.startsWith("image/")) {
                                            viewingAttachment = attachment
                                        } else {
                                            scope.launch {
                                                openMediaExternally(context, attachment)
                                            }
                                        }
                                    },
                                    onAttachmentLongPress = { attachment ->
                                        if (!com.prgramed.emessages.feature.chat.components.isAttachmentSaved(context, attachment)) {
                                            scope.launch {
                                                saveAttachmentToGallery(context, attachment)
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Already saved", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRetryMmsDownload = { mmsId, contentLoc ->
                                        scope.launch {
                                            viewModel.retryMmsDownload(mmsId, contentLoc)
                                        }
                                    },
                                )
                            }
                            is ChatItem.DateHeader -> {
                                DateSeparator(timestamp = item.dateMillis)
                            }
                        }
                    }
                }
            }

            // Reply preview bar
            androidx.compose.animation.AnimatedVisibility(visible = uiState.replyToMessage != null) {
                uiState.replyToMessage?.let { replyMsg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        ) {
                            Text(
                                text = "Reply",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = replyMsg.body.take(80),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.cancelReply() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            MessageInput(
                messageText = uiState.messageText,
                onMessageTextChanged = viewModel::onMessageTextChanged,
                onSendClick = viewModel::sendMessage,
                activeSim = uiState.activeSim,
                availableSims = uiState.availableSims,
                onSimSelected = viewModel::onSimSelected,
                onAttachmentClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
                attachmentUri = uiState.attachmentUri?.let { Uri.parse(it) },
                onClearAttachment = viewModel::onClearAttachment,
                segmentInfo = uiState.segmentInfo,
            )

            // Nav bar spacer: shrinks to 0 when keyboard is open (insets consumed by imePadding)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    // Full-screen media viewer overlay
    viewingAttachment?.let { attachment ->
        MediaViewer(
            attachment = attachment,
            onDismiss = { viewingAttachment = null },
        )
    }
}

private suspend fun openMediaExternally(context: Context, attachment: com.prgramed.emessages.domain.model.Attachment) {
    withContext(Dispatchers.IO) {
        try {
            val sourceUri = Uri.parse(attachment.uri)
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext

            val ext = when {
                attachment.mimeType.contains("vcard", ignoreCase = true) -> "vcf"
                attachment.mimeType.contains("mp4") -> "mp4"
                attachment.mimeType.contains("3gp") -> "3gp"
                attachment.mimeType.startsWith("audio/") -> "m4a"
                attachment.mimeType.startsWith("application/pdf") -> "pdf"
                else -> "bin"
            }
            // Normalize vCard MIME type for intent resolution
            val mimeType = if (attachment.mimeType.contains("vcard", ignoreCase = true)) {
                "text/x-vcard"
            } else {
                attachment.mimeType
            }
            val tempFile = java.io.File(context.cacheDir, "media_${System.currentTimeMillis()}.$ext")
            tempFile.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", tempFile,
            )

            withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
            }
        } catch (_: Exception) { }
    }
}

private sealed class ChatItem {
    data class MessageItem(
        val message: Message,
        val isLastInGroup: Boolean,
    ) : ChatItem()

    data class DateHeader(val dateMillis: Long) : ChatItem()
}

private fun groupMessagesWithDates(reversedMessages: List<Message>): List<ChatItem> {
    if (reversedMessages.isEmpty()) return emptyList()

    val items = mutableListOf<ChatItem>()
    var lastDate: Triple<Int, Int, Int>? = null

    for (i in reversedMessages.indices) {
        val message = reversedMessages[i]
        val cal = Calendar.getInstance().apply { timeInMillis = message.timestamp }
        val currentDate = Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )

        val nextMessage = reversedMessages.getOrNull(i + 1)
        val isLastInGroup = nextMessage == null || nextMessage.type != message.type

        items.add(ChatItem.MessageItem(message, isLastInGroup))

        if (lastDate != currentDate) {
            items.add(ChatItem.DateHeader(message.timestamp))
            lastDate = currentDate
        }
    }

    return items
}
