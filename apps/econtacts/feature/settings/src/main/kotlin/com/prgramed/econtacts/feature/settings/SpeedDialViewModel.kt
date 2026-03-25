package com.prgramed.econtacts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.SpeedDial
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.SpeedDialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpeedDialUiState(
    val speedDials: List<SpeedDial> = emptyList(),
    val contacts: List<Contact> = emptyList(),
)

@HiltViewModel
class SpeedDialViewModel @Inject constructor(
    private val speedDialRepository: SpeedDialRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedDialUiState())
    val uiState: StateFlow<SpeedDialUiState> = _uiState.asStateFlow()

    init {
        observeSpeedDials()
    }

    fun remove(key: Int) {
        viewModelScope.launch {
            speedDialRepository.remove(key)
        }
    }

    fun assign(key: Int, contact: Contact) {
        val phone = contact.phoneNumbers.firstOrNull()?.number ?: return
        viewModelScope.launch {
            speedDialRepository.set(key, contact.id, phone, contact.displayName)
        }
    }

    private var loadContactsJob: Job? = null

    fun loadContacts() {
        loadContactsJob?.cancel()
        loadContactsJob = viewModelScope.launch {
            contactRepository.getAll()
                .catch { }
                .collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts.filter { c -> c.phoneNumbers.isNotEmpty() }) }
                }
        }
    }

    private fun observeSpeedDials() {
        viewModelScope.launch {
            speedDialRepository.getAll()
                .catch { }
                .collect { dials ->
                    _uiState.update { it.copy(speedDials = dials) }
                }
        }
    }
}
