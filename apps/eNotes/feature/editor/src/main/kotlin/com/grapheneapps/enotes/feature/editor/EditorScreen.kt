package com.grapheneapps.enotes.feature.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapheneapps.enotes.feature.editor.components.BlockEditor
import com.grapheneapps.enotes.feature.editor.components.FormatToolbar
import com.grapheneapps.enotes.feature.editor.model.Block
import com.grapheneapps.enotes.feature.editor.model.SpanStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shareText by viewModel.shareText.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle share intent
    shareText?.let { text ->
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share note"))
        viewModel.clearShareText()
    }

    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Track which block is focused for formatting commands
    val focusedBlockId = uiState.focusedBlockId ?: uiState.document.blocks.firstOrNull()?.id

    // Cycle heading levels on toolbar tap
    var headingCycleState = 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isSaved) "" else "Editing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Lock/unlock button
                    IconButton(onClick = { viewModel.toggleLock() }) {
                        Icon(
                            imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (uiState.isLocked) "Unlock note" else "Lock note",
                        )
                    }
                    // Overflow menu
                    var showOverflow by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pin to Top") },
                                onClick = {
                                    viewModel.togglePin()
                                    showOverflow = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share as Markdown") },
                                onClick = {
                                    viewModel.shareAsMarkdown()
                                    showOverflow = false
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.ime)) {
                FormatToolbar(
                    onBold = { focusedBlockId?.let { viewModel.applySpan(it, 0, 0, SpanStyle.BOLD) } },
                    onItalic = { focusedBlockId?.let { viewModel.applySpan(it, 0, 0, SpanStyle.ITALIC) } },
                    onUnderline = { focusedBlockId?.let { viewModel.applySpan(it, 0, 0, SpanStyle.UNDERLINE) } },
                    onStrikethrough = { focusedBlockId?.let { viewModel.applySpan(it, 0, 0, SpanStyle.STRIKETHROUGH) } },
                    onHeading = {
                        focusedBlockId?.let { id ->
                            val block = uiState.document.blocks.find { it.id == id }
                            val nextType = when (block) {
                                is Block.Heading -> when (block.level) {
                                    1 -> "heading2"
                                    2 -> "heading3"
                                    else -> "paragraph"
                                }
                                else -> "heading1"
                            }
                            viewModel.setBlockType(id, nextType)
                        }
                    },
                    onBulletList = { focusedBlockId?.let { viewModel.setBlockType(it, "bullet") } },
                    onNumberedList = { focusedBlockId?.let { viewModel.setBlockType(it, "numbered") } },
                    onChecklist = { focusedBlockId?.let { viewModel.setBlockType(it, "checklist") } },
                    onCodeBlock = { focusedBlockId?.let { viewModel.setBlockType(it, "code") } },
                    onDivider = { viewModel.insertDivider() },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Title field — single line, Enter moves to body
            BasicTextField(
                value = uiState.title,
                onValueChange = { newTitle ->
                    if (newTitle.contains('\n')) {
                        // Enter pressed in title — strip newline and focus first block
                        viewModel.onTitleChanged(newTitle.replace("\n", ""))
                        val firstBlockId = uiState.document.blocks.firstOrNull()?.id
                        if (firstBlockId != null) viewModel.focusBlock(firstBlockId)
                    } else {
                        viewModel.onTitleChanged(newTitle)
                    }
                },
                textStyle = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                ),
                singleLine = false,
                maxLines = 2,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { innerTextField ->
                    if (uiState.title.isEmpty()) {
                        Text(
                            text = "Title",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    innerTextField()
                },
            )

            // Block list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    uiState.document.blocks,
                    key = { _, block -> block.id },
                ) { _, block ->
                    BlockEditor(
                        block = block,
                        shouldFocus = block.id == uiState.focusedBlockId,
                        onTextChanged = { viewModel.onBlockTextChanged(block.id, it) },
                        onEnter = { viewModel.onBlockEnter(block.id) },
                        onDelete = { viewModel.onBlockDelete(block.id) },
                        onCheckToggle = if (block is Block.ChecklistItem) {
                            { viewModel.toggleCheckbox(block.id) }
                        } else null,
                    )
                }
            }
        }
    }

    // Password dialog
    if (uiState.needsPassword) {
        var passwordInput by remember { mutableStateOf("") }
        val title = when (uiState.passwordAction) {
            PasswordAction.VIEW -> "Enter password to view"
            PasswordAction.UNLOCK -> "Enter password to unlock"
            PasswordAction.LOCK -> "Set a password"
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPasswordPrompt() },
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    val errorMsg = uiState.error
                    if (errorMsg != null) {
                        Text(
                            errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onPasswordSubmit(passwordInput) },
                    enabled = passwordInput.isNotBlank(),
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPasswordPrompt() }) {
                    Text("Cancel")
                }
            },
        )
    }
}
