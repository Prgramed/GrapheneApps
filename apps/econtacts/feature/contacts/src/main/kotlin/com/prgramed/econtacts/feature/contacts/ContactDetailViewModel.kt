package com.prgramed.econtacts.feature.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.data.backup.ContactBackupManager
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.VCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailUiState(
    val contact: Contact? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val shareUri: String? = null,
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val vCardRepository: VCardRepository,
    private val backupManager: ContactBackupManager,
) : ViewModel() {

    private val contactId: Long = savedStateHandle["contactId"] ?: 0L

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    init {
        loadContact()
    }

    fun refresh() {
        loadContact()
    }

    fun deleteContact() {
        viewModelScope.launch {
            backupManager.backupContacts(listOf(contactId))
            contactRepository.delete(listOf(contactId))
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun toggleStar() {
        val contact = _uiState.value.contact ?: return
        viewModelScope.launch {
            contactRepository.update(contact.copy(starred = !contact.starred))
            loadContact()
        }
    }

    fun shareVCard() {
        viewModelScope.launch {
            val uri = vCardRepository.exportContacts(listOf(contactId))
            _uiState.update { it.copy(shareUri = uri.toString()) }
        }
    }

    fun onShareHandled() {
        _uiState.update { it.copy(shareUri = null) }
    }

    private fun loadContact() {
        viewModelScope.launch {
            val contact = contactRepository.getById(contactId)
            _uiState.update { it.copy(contact = contact, isLoading = false) }
        }
    }
}
