package com.grapheneapps.enotes.feature.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.grapheneapps.enotes.data.security.BiometricHelper
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

    // Biometric prompt
    LaunchedEffect(uiState.needsBiometric) {
        if (uiState.needsBiometric) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val helper = BiometricHelper()
                if (helper.canAuthenticate(activity)) {
                    helper.authenticate(
                        activity = activity,
                        title = "Unlock Note",
                        subtitle = "Authenticate to view this note",
                        onSuccess = { viewModel.onBiometricSuccess() },
                        onFailed = { viewModel.onBiometricFailed() },
                    )
                } else {
                    viewModel.onBiometricFailed()
                }
            } else {
                viewModel.onBiometricFailed()
            }
        }
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
                    // Lock button with menu
                    Box {
                        IconButton(onClick = { viewModel.onLockIconTap() }) {
                            Icon(
                                imageVector = if (uiState.isLocked || uiState.isUnlockedInMemory) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (uiState.isLocked) "Locked" else "Lock note",
                            )
                        }
                        DropdownMenu(
                            expanded = uiState.showLockMenu,
                            onDismissRequest = { viewModel.dismissLockMenu() },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change Password") },
                                onClick = { viewModel.onLockMenuAction(PasswordAction.CHANGE_PASSWORD) },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove Password") },
                                onClick = { viewModel.onLockMenuAction(PasswordAction.REMOVE_PASSWORD) },
                            )
                        }
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
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

            // Auto-scroll to keep focused block visible — nudge by one line height
            androidx.compose.runtime.LaunchedEffect(uiState.focusedBlockId) {
                val focusId = uiState.focusedBlockId ?: return@LaunchedEffect
                val index = uiState.document.blocks.indexOfFirst { it.id == focusId }
                if (index < 0) return@LaunchedEffect
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (index > lastVisibleIndex) {
                    // Scroll down just enough to reveal the new block (approx one line)
                    listState.animateScrollBy(200f)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
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
                        onPasteLines = { lines -> viewModel.onPasteLines(block.id, lines) },
                    )
                }
            }
        }
    }

    // Password dialog
    if (uiState.needsPassword) {
        var passwordInput by remember { mutableStateOf("") }
        var newPasswordInput by remember { mutableStateOf("") }
        var confirmInput by remember { mutableStateOf("") }

        val action = uiState.passwordAction
        val needsConfirm = action == PasswordAction.SET_PASSWORD || action == PasswordAction.CHANGE_PASSWORD
        val needsNewPassword = action == PasswordAction.CHANGE_PASSWORD
        val passwordsMatch = !needsConfirm || (if (needsNewPassword) newPasswordInput == confirmInput else passwordInput == confirmInput)

        val title = when (action) {
            PasswordAction.VIEW -> "Enter password"
            PasswordAction.SET_PASSWORD -> "Set a password"
            PasswordAction.CHANGE_PASSWORD -> "Change password"
            PasswordAction.REMOVE_PASSWORD -> "Remove password"
        }

        val canSubmit = when (action) {
            PasswordAction.VIEW, PasswordAction.REMOVE_PASSWORD -> passwordInput.isNotBlank()
            PasswordAction.SET_PASSWORD -> passwordInput.isNotBlank() && confirmInput.isNotBlank() && passwordInput == confirmInput
            PasswordAction.CHANGE_PASSWORD -> passwordInput.isNotBlank() && newPasswordInput.isNotBlank() && confirmInput.isNotBlank() && newPasswordInput == confirmInput
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissPasswordPrompt() },
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text(if (needsNewPassword) "Current password" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (needsNewPassword) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPasswordInput,
                            onValueChange = { newPasswordInput = it },
                            placeholder = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    if (needsConfirm) {
                        Spacer(Modifier.height(8.dp))
                        val confirmTarget = if (needsNewPassword) newPasswordInput else passwordInput
                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { confirmInput = it },
                            placeholder = { Text("Confirm password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = confirmInput.isNotBlank() && confirmInput != confirmTarget,
                        )
                        if (confirmInput.isNotBlank() && confirmInput != confirmTarget) {
                            Text("Passwords don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val errorMsg = uiState.error
                    if (errorMsg != null) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (needsNewPassword) viewModel.onPasswordSubmit(passwordInput, newPasswordInput)
                        else viewModel.onPasswordSubmit(passwordInput)
                    },
                    enabled = canSubmit,
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPasswordPrompt() }) { Text("Cancel") }
            },
        )
    }
}
