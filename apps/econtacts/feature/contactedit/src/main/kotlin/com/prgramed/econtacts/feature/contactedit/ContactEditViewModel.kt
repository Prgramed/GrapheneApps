package com.prgramed.econtacts.feature.contactedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.usecase.SaveContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactEditUiState(
    val contactId: Long = 0L,
    val displayName: String = "",
    val phoneNumbers: List<EditablePhone> = listOf(EditablePhone()),
    val emails: List<EditableEmail> = emptyList(),
    val organization: String = "",
    val title: String = "",
    val birthday: String = "",
    val addresses: List<EditableAddress> = emptyList(),
    val websites: List<String> = emptyList(),
    val note: String = "",
    val starred: Boolean = false,
    val photoUri: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

data class EditablePhone(
    val number: String = "",
    val type: PhoneType = PhoneType.MOBILE,
)

data class EditableEmail(
    val address: String = "",
    val type: EmailType = EmailType.HOME,
)

data class EditableAddress(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val type: AddressType = AddressType.HOME,
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val saveContactUseCase: SaveContactUseCase,
) : ViewModel() {

    private val contactId: Long = savedStateHandle["contactId"] ?: 0L
    private val fromVCard: Boolean = savedStateHandle["fromVCard"] ?: false
    private val prefillPhone: String = (savedStateHandle.get<String>("phone") ?: "").let {
        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }

    private val _uiState = MutableStateFlow(ContactEditUiState(contactId = contactId))
    val uiState: StateFlow<ContactEditUiState> = _uiState.asStateFlow()

    init {
        if (contactId != 0L) {
            loadContact()
        } else {
            // Check for pending vCard pre-fill
            VCardImportViewModel.pendingVCardContact?.let { vc ->
                VCardImportViewModel.pendingVCardContact = null
                _uiState.value = ContactEditUiState(
                    displayName = vc.displayName,
                    phoneNumbers = vc.phoneNumbers.map { EditablePhone(it.number, it.type) }
                        .ifEmpty { listOf(EditablePhone()) },
                    emails = vc.emails.map { EditableEmail(it.address, it.type) },
                    organization = vc.organization ?: "",
                    title = vc.title ?: "",
                    birthday = vc.birthday ?: "",
                    addresses = vc.addresses.map {
                        EditableAddress(it.street, it.city, it.region, it.postalCode, it.country, it.type)
                    },
                    websites = vc.websites,
                    note = vc.note ?: "",
                )
            } ?: run {
                if (fromVCard) {
                    _uiState.update { it.copy(error = "Import data lost, please try again") }
                } else if (prefillPhone.isNotBlank()) {
                    _uiState.update {
                        it.copy(phoneNumbers = listOf(EditablePhone(number = prefillPhone)))
                    }
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    // Phone
    fun onPhoneChanged(index: Int, number: String) {
        _uiState.update { state ->
            val phones = state.phoneNumbers.toMutableList()
            if (index < phones.size) phones[index] = phones[index].copy(number = number)
            state.copy(phoneNumbers = phones)
        }
    }

    fun onPhoneTypeChanged(index: Int, type: PhoneType) {
        _uiState.update { state ->
            val phones = state.phoneNumbers.toMutableList()
            if (index < phones.size) phones[index] = phones[index].copy(type = type)
            state.copy(phoneNumbers = phones)
        }
    }

    fun addPhone() {
        _uiState.update { it.copy(phoneNumbers = it.phoneNumbers + EditablePhone()) }
    }

    fun removePhone(index: Int) {
        _uiState.update { state ->
            val phones = state.phoneNumbers.toMutableList()
            if (phones.size > 1) phones.removeAt(index)
            state.copy(phoneNumbers = phones)
        }
    }

    // Email
    fun onEmailChanged(index: Int, address: String) {
        _uiState.update { state ->
            val emails = state.emails.toMutableList()
            if (index < emails.size) emails[index] = emails[index].copy(address = address)
            state.copy(emails = emails)
        }
    }

    fun onEmailTypeChanged(index: Int, type: EmailType) {
        _uiState.update { state ->
            val emails = state.emails.toMutableList()
            if (index < emails.size) emails[index] = emails[index].copy(type = type)
            state.copy(emails = emails)
        }
    }

    fun addEmail() {
        _uiState.update { it.copy(emails = it.emails + EditableEmail()) }
    }

    fun removeEmail(index: Int) {
        _uiState.update { state ->
            val emails = state.emails.toMutableList()
            emails.removeAt(index)
            state.copy(emails = emails)
        }
    }

    // Organization & Title
    fun onOrganizationChanged(value: String) {
        _uiState.update { it.copy(organization = value) }
    }

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    // Birthday
    fun onBirthdayChanged(value: String) {
        _uiState.update { it.copy(birthday = value) }
    }

    // Addresses
    fun onAddressFieldChanged(index: Int, field: String, value: String) {
        _uiState.update { state ->
            val addrs = state.addresses.toMutableList()
            if (index < addrs.size) {
                addrs[index] = when (field) {
                    "street" -> addrs[index].copy(street = value)
                    "city" -> addrs[index].copy(city = value)
                    "region" -> addrs[index].copy(region = value)
                    "postalCode" -> addrs[index].copy(postalCode = value)
                    "country" -> addrs[index].copy(country = value)
                    else -> addrs[index]
                }
            }
            state.copy(addresses = addrs)
        }
    }

    fun onAddressTypeChanged(index: Int, type: AddressType) {
        _uiState.update { state ->
            val addrs = state.addresses.toMutableList()
            if (index < addrs.size) addrs[index] = addrs[index].copy(type = type)
            state.copy(addresses = addrs)
        }
    }

    fun addAddress() {
        _uiState.update { it.copy(addresses = it.addresses + EditableAddress()) }
    }

    fun removeAddress(index: Int) {
        _uiState.update { state ->
            val addrs = state.addresses.toMutableList()
            addrs.removeAt(index)
            state.copy(addresses = addrs)
        }
    }

    // Websites
    fun onWebsiteChanged(index: Int, value: String) {
        _uiState.update { state ->
            val sites = state.websites.toMutableList()
            if (index < sites.size) sites[index] = value
            state.copy(websites = sites)
        }
    }

    fun addWebsite() {
        _uiState.update { it.copy(websites = it.websites + "") }
    }

    fun removeWebsite(index: Int) {
        _uiState.update { state ->
            val sites = state.websites.toMutableList()
            sites.removeAt(index)
            state.copy(websites = sites)
        }
    }

    // Note & Photo
    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onPhotoSelected(uri: String?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun save() {
        _uiState.update { it.copy(error = null) }
        val state = _uiState.value
        if (state.displayName.isBlank() && state.phoneNumbers.all { it.number.isBlank() }) {
            _uiState.update { it.copy(error = "Name or phone number required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val contact = Contact(
                    id = state.contactId,
                    displayName = state.displayName,
                    phoneNumbers = state.phoneNumbers
                        .filter { it.number.isNotBlank() }
                        .map { PhoneNumber(it.number, it.type) },
                    emails = state.emails
                        .filter { it.address.isNotBlank() }
                        .map { Email(it.address, it.type) },
                    organization = state.organization.ifBlank { null },
                    title = state.title.ifBlank { null },
                    birthday = state.birthday.ifBlank { null },
                    addresses = state.addresses
                        .filter { a -> a.street.isNotBlank() || a.city.isNotBlank() }
                        .map { a -> Address(a.street, a.city, a.region, a.postalCode, a.country, a.type) },
                    websites = state.websites.filter { it.isNotBlank() },
                    note = state.note.ifBlank { null },
                    starred = state.starred,
                    photoUri = state.photoUri,
                )
                saveContactUseCase(contact)
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadContact() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val contact = contactRepository.getById(contactId)
            if (contact != null) {
                _uiState.update {
                    it.copy(
                        displayName = contact.displayName,
                        phoneNumbers = contact.phoneNumbers.map { p ->
                            EditablePhone(p.number, p.type)
                        }.ifEmpty { listOf(EditablePhone()) },
                        emails = contact.emails.map { e ->
                            EditableEmail(e.address, e.type)
                        },
                        organization = contact.organization ?: "",
                        title = contact.title ?: "",
                        birthday = contact.birthday ?: "",
                        addresses = contact.addresses.map { a ->
                            EditableAddress(a.street, a.city, a.region, a.postalCode, a.country, a.type)
                        },
                        websites = contact.websites,
                        note = contact.note ?: "",
                        starred = contact.starred,
                        photoUri = contact.photoUri,
                        isLoading = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Contact not found") }
            }
        }
    }
}
