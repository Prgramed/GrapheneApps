package com.grapheneapps.enotes.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.data.security.CryptoManager
import com.grapheneapps.enotes.domain.repository.NoteRepository
import com.grapheneapps.enotes.feature.editor.model.Block
import com.grapheneapps.enotes.feature.editor.model.DocumentSerializer
import com.grapheneapps.enotes.feature.editor.model.RichTextDocument
import com.grapheneapps.enotes.feature.editor.model.SpanInfo
import com.grapheneapps.enotes.feature.editor.model.SpanStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PasswordAction { VIEW, SET_PASSWORD, CHANGE_PASSWORD, REMOVE_PASSWORD }

data class EditorUiState(
    val noteId: String = "",
    val title: String = "",
    val document: RichTextDocument = RichTextDocument(),
    val focusedBlockId: String? = null,
    val isLoading: Boolean = true,
    val isSaved: Boolean = true,
    val isLocked: Boolean = false,
    val isUnlockedInMemory: Boolean = false,
    val needsPassword: Boolean = false,
    val needsBiometric: Boolean = false,
    val showLockMenu: Boolean = false,
    val passwordAction: PasswordAction = PasswordAction.VIEW,
    val error: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val cryptoManager: CryptoManager,
) : ViewModel() {

    private val noteId: String = savedStateHandle["noteId"] ?: ""

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<RichTextDocument>()
    private val redoStack = mutableListOf<RichTextDocument>()
    private var saveJob: Job? = null
    private var isDirty = false
    private var autoLockJob: Job? = null

    private fun startAutoLockTimer() {
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10 * 60 * 1000L) // 10 minutes
            _uiState.update {
                if (it.isUnlockedInMemory) {
                    val hasCached = cryptoManager.getCachedPassword(it.noteId) != null
                    it.copy(
                        document = RichTextDocument(),
                        isUnlockedInMemory = false,
                        needsBiometric = hasCached,
                        needsPassword = !hasCached,
                        passwordAction = PasswordAction.VIEW,
                    )
                } else it
            }
        }
    }

    init {
        viewModelScope.launch {
            val note = noteRepository.getById(noteId)
            if (note != null) {
                if (note.isLocked && note.encryptedBody != null) {
                    val hasCached = cryptoManager.getCachedPassword(note.id) != null
                    _uiState.value = EditorUiState(
                        noteId = note.id,
                        title = note.title,
                        isLoading = false,
                        isLocked = true,
                        needsBiometric = hasCached, // try biometric if password is cached
                        needsPassword = !hasCached, // fall back to password if not cached
                        passwordAction = PasswordAction.VIEW,
                    )
                } else {
                    val doc = DocumentSerializer.fromJson(note.bodyJson)
                    _uiState.value = EditorUiState(
                        noteId = note.id,
                        title = note.title,
                        document = doc,
                        isLoading = false,
                        isLocked = note.isLocked,
                    )
                }
            } else {
                _uiState.value = EditorUiState(
                    noteId = noteId,
                    isLoading = false,
                )
            }
        }
    }

    fun onTitleChanged(title: String) {
        isDirty = true
        _uiState.update { it.copy(title = title, isSaved = false) }
        scheduleSave()
    }

    fun focusBlock(blockId: String) {
        _uiState.update { it.copy(focusedBlockId = blockId) }
    }

    fun onBlockTextChanged(blockId: String, newText: String) {
        isDirty = true
        pushUndo()
        val blocks = _uiState.value.document.blocks.map { block ->
            if (block.id == blockId) {
                // Adjust spans for text length changes
                val spans = block.spans.filter { it.end <= newText.length }
                when (block) {
                    is Block.Paragraph -> block.copy(text = newText, spans = spans)
                    is Block.Heading -> block.copy(text = newText, spans = spans)
                    is Block.BulletItem -> block.copy(text = newText, spans = spans)
                    is Block.NumberedItem -> block.copy(text = newText, spans = spans)
                    is Block.ChecklistItem -> block.copy(text = newText, spans = spans)
                    is Block.Image -> block.copy(text = newText)
                    is Block.CodeBlock -> block.copy(text = newText, spans = spans)
                    is Block.Table -> block
                    is Block.Divider -> block
                }
            } else block
        }
        _uiState.update { it.copy(document = RichTextDocument(blocks), isSaved = false) }
        scheduleSave()
    }

    fun onBlockEnter(blockId: String) {
        pushUndo()
        val blocks = _uiState.value.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return

        val current = blocks[index]
        val newBlock = when (current) {
            is Block.BulletItem -> Block.BulletItem(indent = current.indent)
            is Block.NumberedItem -> Block.NumberedItem(number = current.number + 1, indent = current.indent)
            is Block.ChecklistItem -> Block.ChecklistItem()
            else -> Block.Paragraph()
        }
        blocks.add(index + 1, newBlock)
        _uiState.update {
            it.copy(
                document = RichTextDocument(blocks),
                focusedBlockId = newBlock.id,
                isSaved = false,
            )
        }
        scheduleSave()
    }

    fun onPasteLines(afterBlockId: String, lines: List<String>) {
        pushUndo()
        val blocks = _uiState.value.document.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == afterBlockId }
        if (index < 0) return

        val newBlocks = lines.map { line -> Block.Paragraph(text = line) }
        blocks.addAll(index + 1, newBlocks)
        val lastNew = newBlocks.lastOrNull()
        _uiState.update {
            it.copy(
                document = RichTextDocument(blocks),
                focusedBlockId = lastNew?.id,
                isSaved = false,
            )
        }
        scheduleSave()
    }

    fun onBlockDelete(blockId: String) {
        val blocks = _uiState.value.document.blocks
        if (blocks.size <= 1) return
        pushUndo()
        isDirty = true
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return

        val newBlocks = blocks.toMutableList()
        val deletedBlock = newBlocks.removeAt(index)

        // Merge text into previous block
        val focusId: String?
        if (index > 0 && deletedBlock.text.isNotEmpty()) {
            val prev = newBlocks[index - 1]
            val mergedText = prev.text + deletedBlock.text
            newBlocks[index - 1] = when (prev) {
                is Block.Paragraph -> prev.copy(text = mergedText)
                is Block.Heading -> prev.copy(text = mergedText)
                is Block.BulletItem -> prev.copy(text = mergedText)
                is Block.NumberedItem -> prev.copy(text = mergedText)
                is Block.ChecklistItem -> prev.copy(text = mergedText)
                is Block.CodeBlock -> prev.copy(text = mergedText)
                else -> prev
            }
            focusId = prev.id
        } else {
            focusId = if (index > 0) newBlocks[index - 1].id else newBlocks.firstOrNull()?.id
        }

        _uiState.update {
            it.copy(document = RichTextDocument(newBlocks), focusedBlockId = focusId, isSaved = false)
        }
        scheduleSave()
    }

    fun toggleCheckbox(blockId: String) {
        val blocks = _uiState.value.document.blocks.map { block ->
            if (block.id == blockId && block is Block.ChecklistItem) {
                block.copy(checked = !block.checked)
            } else block
        }
        _uiState.update { it.copy(document = RichTextDocument(blocks), isSaved = false) }
        scheduleSave()
    }

    fun setBlockType(blockId: String, type: String) {
        pushUndo()
        val blocks = _uiState.value.document.blocks.map { block ->
            if (block.id == blockId) {
                when (type) {
                    "paragraph" -> Block.Paragraph(id = block.id, text = block.text, spans = block.spans)
                    "heading1" -> Block.Heading(id = block.id, text = block.text, spans = block.spans, level = 1)
                    "heading2" -> Block.Heading(id = block.id, text = block.text, spans = block.spans, level = 2)
                    "heading3" -> Block.Heading(id = block.id, text = block.text, spans = block.spans, level = 3)
                    "bullet" -> Block.BulletItem(id = block.id, text = block.text, spans = block.spans)
                    "numbered" -> Block.NumberedItem(id = block.id, text = block.text, spans = block.spans)
                    "checklist" -> Block.ChecklistItem(id = block.id, text = block.text, spans = block.spans)
                    "code" -> Block.CodeBlock(id = block.id, text = block.text, spans = block.spans)
                    else -> block
                }
            } else block
        }
        _uiState.update { it.copy(document = RichTextDocument(blocks), isSaved = false) }
        scheduleSave()
    }

    fun applySpan(blockId: String, start: Int, end: Int, style: SpanStyle) {
        if (start >= end) return
        pushUndo()
        val blocks = _uiState.value.document.blocks.map { block ->
            if (block.id == blockId) {
                val spans = block.spans.toMutableList()
                // Toggle: remove if exact span exists, otherwise add
                val existing = spans.find { it.start == start && it.end == end && it.style == style }
                if (existing != null) {
                    spans.remove(existing)
                } else {
                    spans.add(SpanInfo(start, end, style))
                }
                when (block) {
                    is Block.Paragraph -> block.copy(spans = spans)
                    is Block.Heading -> block.copy(spans = spans)
                    is Block.BulletItem -> block.copy(spans = spans)
                    is Block.NumberedItem -> block.copy(spans = spans)
                    is Block.ChecklistItem -> block.copy(spans = spans)
                    is Block.Image -> block
                    is Block.CodeBlock -> block.copy(spans = spans)
                    is Block.Table -> block
                    is Block.Divider -> block
                }
            } else block
        }
        _uiState.update { it.copy(document = RichTextDocument(blocks), isSaved = false) }
        scheduleSave()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(_uiState.value.document)
        _uiState.update { it.copy(document = undoStack.removeLast(), isSaved = false) }
        scheduleSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(_uiState.value.document)
        _uiState.update { it.copy(document = redoStack.removeLast(), isSaved = false) }
        scheduleSave()
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            val state = _uiState.value
            val noteId = state.noteId
            val cached = cryptoManager.getCachedPassword(noteId)
            if (cached != null) {
                onPasswordSubmit(cached)
            } else {
                // No cached password — fall back to manual entry
                _uiState.update { it.copy(needsBiometric = false, needsPassword = true) }
            }
        }
    }

    fun onBiometricFailed() {
        _uiState.update { it.copy(needsBiometric = false, needsPassword = true) }
    }

    fun onPasswordSubmit(password: String, newPassword: String? = null) {
        viewModelScope.launch {
            val state = _uiState.value
            val note = noteRepository.getById(state.noteId) ?: return@launch

            when (state.passwordAction) {
                PasswordAction.VIEW -> {
                    // Decrypt in memory only — note stays encrypted on disk
                    val encrypted = note.encryptedBody ?: return@launch
                    try {
                        val decrypted = cryptoManager.decrypt(encrypted, password)
                        val doc = DocumentSerializer.fromJson(decrypted)
                        cryptoManager.cachePassword(note.id, password)
                        _uiState.update {
                            it.copy(document = doc, isUnlockedInMemory = true, needsPassword = false, needsBiometric = false, error = null)
                        }
                        startAutoLockTimer()
                    } catch (_: Exception) {
                        _uiState.update { it.copy(error = "Wrong password") }
                    }
                }

                PasswordAction.SET_PASSWORD -> {
                    // First-time lock — encrypt and save
                    val bodyJson = DocumentSerializer.toJson(state.document)
                    val encrypted = cryptoManager.encrypt(bodyJson, password)
                    noteRepository.save(
                        note.copy(isLocked = true, bodyJson = "", encryptedBody = encrypted, editedAt = System.currentTimeMillis()),
                    )
                    cryptoManager.cachePassword(note.id, password)
                    _uiState.update { it.copy(isLocked = true, isUnlockedInMemory = false, needsPassword = false, error = null) }
                }

                PasswordAction.REMOVE_PASSWORD -> {
                    // Verify old password then permanently unlock
                    val encrypted = note.encryptedBody ?: return@launch
                    try {
                        val decrypted = cryptoManager.decrypt(encrypted, password)
                        noteRepository.save(
                            note.copy(isLocked = false, bodyJson = decrypted, encryptedBody = null, editedAt = System.currentTimeMillis()),
                        )
                        cryptoManager.clearCachedPassword(note.id)
                        autoLockJob?.cancel()
                        val doc = DocumentSerializer.fromJson(decrypted)
                        _uiState.update {
                            it.copy(isLocked = false, isUnlockedInMemory = false, document = doc, needsPassword = false, error = null)
                        }
                    } catch (_: Exception) {
                        _uiState.update { it.copy(error = "Wrong password") }
                    }
                }

                PasswordAction.CHANGE_PASSWORD -> {
                    // Verify old password, re-encrypt with new
                    val encrypted = note.encryptedBody ?: return@launch
                    val np = newPassword ?: return@launch
                    try {
                        val decrypted = cryptoManager.decrypt(encrypted, password)
                        val reEncrypted = cryptoManager.encrypt(decrypted, np)
                        noteRepository.save(
                            note.copy(encryptedBody = reEncrypted, editedAt = System.currentTimeMillis()),
                        )
                        cryptoManager.cachePassword(note.id, np)
                        _uiState.update { it.copy(needsPassword = false, error = null) }
                    } catch (_: Exception) {
                        _uiState.update { it.copy(error = "Wrong password") }
                    }
                }
            }
        }
    }

    fun onLockIconTap() {
        val state = _uiState.value
        if (state.isLocked || state.isUnlockedInMemory) {
            // Show menu: Change Password / Remove Password
            _uiState.update { it.copy(showLockMenu = true) }
        } else {
            // Not locked — set password
            _uiState.update { it.copy(needsPassword = true, passwordAction = PasswordAction.SET_PASSWORD, error = null) }
        }
    }

    fun onLockMenuAction(action: PasswordAction) {
        _uiState.update { it.copy(showLockMenu = false, needsPassword = true, passwordAction = action, error = null) }
    }

    fun dismissLockMenu() {
        _uiState.update { it.copy(showLockMenu = false) }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(needsPassword = false, error = null) }
    }

    fun insertDivider() {
        pushUndo()
        val blocks = _uiState.value.document.blocks.toMutableList()
        val focusId = _uiState.value.focusedBlockId
        val index = if (focusId != null) blocks.indexOfFirst { it.id == focusId } + 1 else blocks.size
        val divider = Block.Divider()
        val newParagraph = Block.Paragraph()
        blocks.add(index, divider)
        blocks.add(index + 1, newParagraph)
        _uiState.update {
            it.copy(document = RichTextDocument(blocks), focusedBlockId = newParagraph.id, isSaved = false)
        }
        scheduleSave()
    }

    fun togglePin() {
        viewModelScope.launch {
            val note = noteRepository.getById(_uiState.value.noteId) ?: return@launch
            noteRepository.save(note.copy(isPinned = !note.isPinned, editedAt = System.currentTimeMillis()))
        }
    }

    fun shareAsMarkdown() {
        // Returns markdown text — caller can use share intent
        val doc = _uiState.value.document
        val markdown = com.grapheneapps.enotes.feature.editor.model.MarkdownConverter.toMarkdown(doc)
        _shareText.value = "# ${_uiState.value.title}\n\n$markdown"
    }

    private val _shareText = MutableStateFlow<String?>(null)
    val shareText: StateFlow<String?> = _shareText.asStateFlow()

    fun clearShareText() { _shareText.value = null }

    fun saveNow() {
        saveJob?.cancel()
        viewModelScope.launch { performSave() }
    }

    private fun pushUndo() {
        undoStack.add(_uiState.value.document)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(3000)
            performSave()
        }
    }

    private suspend fun performSave() {
        if (!isDirty) return
        val state = _uiState.value
        val bodyJson = DocumentSerializer.toJson(state.document)
        val title = state.title.ifBlank {
            state.document.blocks.firstOrNull()?.text?.take(50) ?: "Untitled"
        }
        val note = noteRepository.getById(state.noteId)
        if (note != null) {
            noteRepository.save(
                note.copy(
                    title = title,
                    bodyJson = bodyJson,
                    editedAt = System.currentTimeMillis(),
                    syncStatus = com.grapheneapps.enotes.domain.model.SyncStatus.PENDING_UPLOAD,
                ),
            )
        }
        _uiState.update { it.copy(title = title, isSaved = true) }
    }
}
