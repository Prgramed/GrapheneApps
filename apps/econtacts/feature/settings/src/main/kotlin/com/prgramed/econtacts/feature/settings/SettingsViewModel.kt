package com.prgramed.econtacts.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.VCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val importResult: Int? = null,
    val exportUri: String? = null,
    val isExporting: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vCardRepository: VCardRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var exportJob: Job? = null

    fun importContacts(uri: Uri) {
        viewModelScope.launch {
            val count = vCardRepository.importContacts(uri)
            _uiState.update { it.copy(importResult = count) }
        }
    }

    fun onExportHandled() {
        _uiState.update { it.copy(exportUri = null) }
    }

    fun exportAllContacts() {
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val contacts = contactRepository.getAll().first()
            val ids = contacts.map { it.id }
            if (ids.isNotEmpty()) {
                val uri = vCardRepository.exportContacts(ids)
                _uiState.update { it.copy(exportUri = uri.toString(), isExporting = false) }
            } else {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(isExporting = false) }
    }
}
