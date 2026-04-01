package dev.equran.ui.memorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.domain.model.AyahWithTranslations
import dev.equran.domain.model.MemorizedVerse
import dev.equran.domain.repository.MemorizationRepository
import dev.equran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewItem(val verse: MemorizedVerse, val ayah: AyahWithTranslations)

data class PracticeUiState(
    val items: List<ReviewItem> = emptyList(),
    val currentIndex: Int = 0,
    val isRevealed: Boolean = false,
    val isComplete: Boolean = false,
    val isLoading: Boolean = true,
    val ratings: MutableMap<Int, Int> = mutableMapOf(), // index → confidence
    val fontSize: Float = 26f,
) {
    val current: ReviewItem? get() = items.getOrNull(currentIndex)
    val total: Int get() = items.size
    val againCount: Int get() = ratings.values.count { it == 1 }
    val goodCount: Int get() = ratings.values.count { it == 2 }
    val easyCount: Int get() = ratings.values.count { it == 3 }
}

@HiltViewModel
class MemorizationPracticeViewModel @Inject constructor(
    private val memorizationRepository: MemorizationRepository,
    private val quranRepository: QuranRepository,
    private val preferencesRepository: QuranPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            val queue = memorizationRepository.getReviewQueue(20)
            val items = queue.mapNotNull { verse ->
                val surahVerses = quranRepository.getVerses(verse.surah, settings.arabicScript, settings.enabledTranslations)
                val ayah = surahVerses.find { it.ayah == verse.ayah } ?: return@mapNotNull null
                ReviewItem(verse, ayah)
            }
            _uiState.value = PracticeUiState(items = items, isLoading = false, fontSize = settings.fontSize)
        }
    }

    fun reveal() {
        _uiState.value = _uiState.value.copy(isRevealed = true)
    }

    fun rate(confidence: Int) {
        val state = _uiState.value
        val current = state.current ?: return

        viewModelScope.launch {
            memorizationRepository.updateReview(current.verse.surah, current.verse.ayah, confidence)
        }

        val newRatings = state.ratings.toMutableMap()
        newRatings[state.currentIndex] = confidence

        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.total) {
            _uiState.value = state.copy(isComplete = true, ratings = newRatings)
        } else {
            _uiState.value = state.copy(currentIndex = nextIndex, isRevealed = false, ratings = newRatings)
        }
    }
}
