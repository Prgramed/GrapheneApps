package com.prgramed.econtacts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prgramed.econtacts.data.backup.ContactBackupManager
import com.prgramed.econtacts.domain.model.DuplicateGroup
import com.prgramed.econtacts.domain.usecase.FindDuplicatesUseCase
import com.prgramed.econtacts.domain.repository.DuplicateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicatesUiState(
    val duplicates: List<DuplicateGroup> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val duplicateRepository: DuplicateRepository,
    private val backupManager: ContactBackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    init {
        findDuplicates()
    }

    fun merge(primaryId: Long, mergeIds: List<Long>) {
        viewModelScope.launch {
            backupManager.backupContacts(mergeIds)
            duplicateRepository.mergeContacts(primaryId, mergeIds)
            findDuplicates()
        }
    }

    fun mergeAll() {
        viewModelScope.launch {
            val allMergeIds = _uiState.value.duplicates.flatMap { group ->
                group.contacts.drop(1).map { it.id }
            }
            backupManager.backupContacts(allMergeIds)
            _uiState.value.duplicates.forEach { group ->
                val primary = group.contacts.first()
                val others = group.contacts.drop(1).map { it.id }
                duplicateRepository.mergeContacts(primary.id, others)
            }
            findDuplicates()
        }
    }

    private fun findDuplicates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            findDuplicatesUseCase()
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { duplicates ->
                    _uiState.update { it.copy(duplicates = duplicates, isLoading = false) }
                }
        }
    }
}
