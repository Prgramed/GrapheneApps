package com.grapheneapps.enotes.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.repository.FolderRepository
import com.grapheneapps.enotes.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class SortOrder(val label: String) {
    DATE_EDITED("Date Edited"),
    DATE_CREATED("Date Created"),
    TITLE_AZ("Title A\u2013Z"),
    TITLE_ZA("Title Z\u2013A"),
}

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DATE_EDITED,
    val isGalleryView: Boolean = false,
)

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteListUiState())
    val uiState: StateFlow<NoteListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadNotes(folderId: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val flow = when (folderId) {
                null -> noteRepository.observeAll()
                "__pinned__" -> noteRepository.observeAll().map { notes -> notes.filter { it.isPinned } }
                "__locked__" -> noteRepository.observeLocked()
                "__conflicts__" -> noteRepository.observeConflicts()
                else -> noteRepository.observeByFolder(folderId)
            }
            flow.collect { notes ->
                val sorted = sortNotes(notes, _uiState.value.sortOrder)
                _uiState.value = _uiState.value.copy(notes = sorted, isLoading = false)
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(
            sortOrder = order,
            notes = sortNotes(_uiState.value.notes, order),
        )
    }

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGalleryView = !_uiState.value.isGalleryView)
    }

    fun createNote(folderId: String?): String {
        val id = UUID.randomUUID().toString()
        val actualFolderId = if (folderId?.startsWith("__") == true) null else folderId
        val note = Note(id = id, title = "", folderId = actualFolderId)
        viewModelScope.launch { noteRepository.save(note) }
        return id
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { noteRepository.softDelete(id) }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            noteRepository.save(note.copy(isPinned = !note.isPinned, editedAt = System.currentTimeMillis()))
        }
    }

    fun moveNoteToFolder(noteId: String, folderId: String?) {
        viewModelScope.launch {
            val note = noteRepository.getById(noteId) ?: return@launch
            noteRepository.save(note.copy(folderId = folderId, editedAt = System.currentTimeMillis()))
        }
    }

    private fun sortNotes(notes: List<Note>, order: SortOrder): List<Note> = when (order) {
        SortOrder.DATE_EDITED -> notes.sortedByDescending { it.editedAt }
        SortOrder.DATE_CREATED -> notes.sortedByDescending { it.createdAt }
        SortOrder.TITLE_AZ -> notes.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_ZA -> notes.sortedByDescending { it.title.lowercase() }
    }
}
