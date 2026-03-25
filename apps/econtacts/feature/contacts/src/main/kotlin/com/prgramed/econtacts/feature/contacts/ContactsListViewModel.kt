package com.prgramed.econtacts.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.data.backup.ContactBackupManager
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.DuplicateRepository
import com.prgramed.econtacts.domain.usecase.DeleteContactsUseCase
import com.prgramed.econtacts.domain.usecase.GetContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SortMode(val label: String) {
    NAME_AZ("Name A-Z"),
    NAME_ZA("Name Z-A"),
    RECENTLY_ADDED("Recently added"),
}

data class ContactsListUiState(
    val contacts: List<Contact> = emptyList(),
    val filteredContacts: List<Contact> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NAME_AZ,
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val undoBackupFile: File? = null,
)

@HiltViewModel
class ContactsListViewModel @Inject constructor(
    private val getContactsUseCase: GetContactsUseCase,
    private val deleteContactsUseCase: DeleteContactsUseCase,
    private val backupManager: ContactBackupManager,
    private val duplicateRepository: DuplicateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsListUiState())
    val uiState: StateFlow<ContactsListUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startObserving()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Contacts permission required") }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredContacts = applyFilterAndSort(state.contacts, query, state.sortMode),
            )
        }
    }

    fun onSortModeChanged(mode: SortMode) {
        _uiState.update { state ->
            state.copy(
                sortMode = mode,
                filteredContacts = applyFilterAndSort(state.contacts, state.searchQuery, mode),
            )
        }
    }

    fun toggleSelection(contactId: Long) {
        _uiState.update { state ->
            val newSelection = if (contactId in state.selectedIds) {
                state.selectedIds - contactId
            } else {
                state.selectedIds + contactId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val backupFile = backupManager.backupContacts(ids)
            deleteContactsUseCase(ids)
            _uiState.update {
                it.copy(selectedIds = emptySet(), undoBackupFile = backupFile)
            }
        }
    }

    fun mergeSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.size < 2) return
        val primaryId = ids.first()
        val mergeIds = ids.drop(1)
        viewModelScope.launch {
            try {
                val backupFile = backupManager.backupContacts(mergeIds)
                duplicateRepository.mergeContacts(primaryId, mergeIds)
                _uiState.update {
                    it.copy(selectedIds = emptySet(), undoBackupFile = backupFile)
                }
            } catch (_: Exception) {
                // Merge failed
            }
        }
    }

    fun undoDelete() {
        val file = _uiState.value.undoBackupFile ?: return
        viewModelScope.launch {
            backupManager.restoreFromBackup(file)
            _uiState.update { it.copy(undoBackupFile = null) }
        }
    }

    fun dismissUndo() {
        _uiState.update { it.copy(undoBackupFile = null) }
    }

    private fun startObserving() {
        viewModelScope.launch {
            getContactsUseCase()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { contacts ->
                    _uiState.update { state ->
                        state.copy(
                            contacts = contacts,
                            filteredContacts = applyFilterAndSort(contacts, state.searchQuery, state.sortMode),
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    private fun applyFilterAndSort(
        contacts: List<Contact>,
        query: String,
        sortMode: SortMode,
    ): List<Contact> {
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.displayName.contains(query, ignoreCase = true) ||
                    contact.phoneNumbers.any { it.number.contains(query) }
            }
        }
        return when (sortMode) {
            SortMode.NAME_AZ -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_ZA -> filtered.sortedByDescending { it.displayName.lowercase() }
            SortMode.RECENTLY_ADDED -> filtered.sortedByDescending { it.id }
        }
    }
}
