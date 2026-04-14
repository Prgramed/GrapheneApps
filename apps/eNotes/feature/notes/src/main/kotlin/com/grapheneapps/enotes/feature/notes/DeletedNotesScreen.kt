package com.grapheneapps.enotes.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.grapheneapps.enotes.domain.model.Note
import com.grapheneapps.enotes.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeletedNotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    // WhileSubscribed(5s) lets Room release the observer shortly after the
    // screen leaves the back stack.
    val notes: StateFlow<List<Note>> = noteRepository.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: String) {
        viewModelScope.launch { noteRepository.restore(id) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { noteRepository.softDelete(id) }
    }

    fun permanentlyDelete(id: String) {
        viewModelScope.launch { noteRepository.permanentlyDelete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedNotesScreen(
    onNoteClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DeletedNotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var noteToPermDelete by remember { mutableStateOf<Note?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (noteToPermDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToPermDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("\"${noteToPermDelete?.title?.ifBlank { "Untitled" }}\" will be permanently deleted and cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    noteToPermDelete?.let { viewModel.permanentlyDelete(it.id) }
                    noteToPermDelete = null
                    scope.launch { snackbarHostState.showSnackbar("Note permanently deleted") }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { noteToPermDelete = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently Deleted") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No deleted notes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(notes, key = { it.id }) { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(note.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            val daysAgo = ((System.currentTimeMillis() - (note.deletedAt ?: 0)) / 86_400_000).toInt()
                            val daysRemaining = (30 - daysAgo).coerceAtLeast(0)
                            Text(
                                text = "$daysRemaining days remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            viewModel.restore(note.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Note restored",
                                    actionLabel = "Undo",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    // Undoing a restore → soft delete it again
                                    // (we don't have a direct reference but the id is captured)
                                    viewModel.softDelete(note.id)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.RestoreFromTrash,
                                contentDescription = "Restore",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { noteToPermDelete = note }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Delete permanently",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
