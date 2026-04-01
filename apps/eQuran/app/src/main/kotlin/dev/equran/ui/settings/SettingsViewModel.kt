package dev.equran.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.data.preferences.QuranSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: QuranPreferencesRepository,
) : ViewModel() {

    val settings: StateFlow<QuranSettings> = preferencesRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuranSettings())

    fun setArabicScript(script: String) {
        viewModelScope.launch { preferencesRepository.setArabicScript(script) }
    }

    fun toggleTranslation(edition: String) {
        viewModelScope.launch {
            val current = settings.value.enabledTranslations.toMutableList()
            if (edition in current) {
                if (current.size > 1) current.remove(edition) // Keep at least one
            } else {
                current.add(edition)
            }
            preferencesRepository.setEnabledTranslations(current)
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { preferencesRepository.setFontSize(size) }
    }

    fun setSelectedTafsir(tafsir: String) {
        viewModelScope.launch { preferencesRepository.setSelectedTafsir(tafsir) }
    }

    fun setQuranIndexServerUrl(url: String) {
        viewModelScope.launch { preferencesRepository.setQuranIndexServerUrl(url.trim()) }
    }
}
