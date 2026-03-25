package com.prgramed.econtacts.feature.contactedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.usecase.SaveContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VCardImportViewModel @Inject constructor(
    val saveContactUseCase: SaveContactUseCase,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showPicker = MutableStateFlow(false)
    val showPicker: StateFlow<Boolean> = _showPicker.asStateFlow()

    private val _duplicates = MutableStateFlow<List<Contact>>(emptyList())
    val duplicates: StateFlow<List<Contact>> = _duplicates.asStateFlow()

    fun checkDuplicates(contact: Contact) {
        viewModelScope.launch {
            val allContacts = contactRepository.getAll().first()
            val vcardPhones = contact.phoneNumbers.map { it.number.replace(Regex("[^\\d+]"), "") }.toSet()
            val vcardName = contact.displayName.lowercase().trim()

            val matches = allContacts.filter { existing ->
                // Match by name
                val nameMatch = vcardName.isNotBlank() &&
                    existing.displayName.lowercase().trim() == vcardName
                // Match by phone
                val phoneMatch = existing.phoneNumbers.any { ep ->
                    ep.number.replace(Regex("[^\\d+]"), "") in vcardPhones
                }
                nameMatch || phoneMatch
            }
            _duplicates.value = matches
        }
    }

    fun showContactPicker() {
        _showPicker.value = true
        loadContacts("")
    }

    fun dismissPicker() {
        _showPicker.value = false
    }

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
        loadContacts(query)
    }

    private fun loadContacts(query: String) {
        viewModelScope.launch {
            val flow = if (query.isBlank()) contactRepository.getAll() else contactRepository.search(query)
            _contacts.value = flow.first()
        }
    }

    suspend fun saveAsNew(contact: Contact): Long =
        saveContactUseCase(contact)

    suspend fun mergeIntoExisting(vcardContact: Contact, existingContactId: Long) {
        val existing = contactRepository.getById(existingContactId) ?: return
        val mergedPhones = existing.phoneNumbers.toMutableList()
        for (phone in vcardContact.phoneNumbers) {
            if (mergedPhones.none { it.number == phone.number }) {
                mergedPhones.add(phone)
            }
        }
        val mergedEmails = existing.emails.toMutableList()
        for (email in vcardContact.emails) {
            if (mergedEmails.none { it.address == email.address }) {
                mergedEmails.add(email)
            }
        }
        val mergedWebsites = (existing.websites + vcardContact.websites).distinct()
        val mergedAddresses = existing.addresses.toMutableList()
        vcardContact.addresses.forEach { addr ->
            if (mergedAddresses.none { it.street == addr.street && it.city == addr.city }) {
                mergedAddresses.add(addr)
            }
        }
        val merged = existing.copy(
            phoneNumbers = mergedPhones,
            emails = mergedEmails,
            websites = mergedWebsites,
            addresses = mergedAddresses,
            organization = existing.organization ?: vcardContact.organization,
            title = existing.title ?: vcardContact.title,
            note = existing.note ?: vcardContact.note,
            birthday = existing.birthday ?: vcardContact.birthday,
        )
        contactRepository.update(merged)
    }

    companion object {
        // Shared holder for passing parsed vCard to ContactEditScreen
        var pendingVCardContact: Contact? = null
    }
}
