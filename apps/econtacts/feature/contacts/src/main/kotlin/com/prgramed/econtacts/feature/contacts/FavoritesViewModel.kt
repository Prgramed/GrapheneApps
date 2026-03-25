package com.prgramed.econtacts.feature.contacts

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val favorites: List<Contact> = emptyList(),
    val allContacts: List<Contact> = emptyList(),
    val speedDials: List<SpeedDial> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val speedDialRepository: SpeedDialRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startObserving()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Contacts permission required") }
        }
    }

    fun assignSpeedDial(key: Int, contactId: Long, number: String, displayName: String) {
        viewModelScope.launch {
            speedDialRepository.set(key, contactId, number, displayName)
        }
    }

    fun removeSpeedDial(key: Int) {
        viewModelScope.launch {
            speedDialRepository.remove(key)
        }
    }

    private var observeJob: Job? = null

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                contactRepository.getStarred(),
                contactRepository.getAll(),
                speedDialRepository.getAll(),
            ) { favorites, allContacts, speedDials ->
                FavoritesUiState(
                    favorites = favorites,
                    allContacts = allContacts,
                    speedDials = speedDials,
                    isLoading = false,
                )
            }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }
}
