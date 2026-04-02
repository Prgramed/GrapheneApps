package dev.equran.ui.topics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.domain.model.AyahWithTranslations
import dev.equran.domain.model.Bookmark
import dev.equran.domain.model.MemorizedVerse
import dev.equran.domain.model.Topic
import dev.equran.domain.repository.BookmarkRepository
import dev.equran.domain.repository.MemorizationRepository
import dev.equran.domain.repository.QuranRepository
import dev.equran.domain.repository.TopicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopicDetailUiState(
    val topic: Topic? = null,
    val verses: List<AyahWithTranslations> = emptyList(),
    val surahCount: Int = 0,
    val isLoading: Boolean = true,
    val fontSize: Float = 26f,
)

@HiltViewModel
class TopicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val topicRepository: TopicRepository,
    private val quranRepository: QuranRepository,
    private val preferencesRepository: QuranPreferencesRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val memorizationRepository: MemorizationRepository,
) : ViewModel() {

    val allBookmarks = bookmarkRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMemorized = memorizationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBookmark(surah: Int, ayah: Int) {
        viewModelScope.launch {
            if (bookmarkRepository.isBookmarked(surah, ayah)) bookmarkRepository.remove(surah, ayah)
            else bookmarkRepository.add(surah, ayah)
        }
    }

    fun toggleMemorized(surah: Int, ayah: Int) {
        viewModelScope.launch {
            val existing = allMemorized.value.find { it.surah == surah && it.ayah == ayah }
            if (existing != null) memorizationRepository.removeMemorized(surah, ayah)
            else memorizationRepository.markMemorized(surah, ayah)
        }
    }

    private val topicId: Long = savedStateHandle["topicId"] ?: 0

    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (topicId <= 0) {
                    _uiState.value = TopicDetailUiState(isLoading = false)
                    return@launch
                }
                val settings = preferencesRepository.settings.first()
                val topics = topicRepository.getAll()
                val topic = topics.find { it.id == topicId }
                val verseRefs = topicRepository.getTopicVerses(topicId)

                val allVerses = mutableListOf<AyahWithTranslations>()
                val bySurah = verseRefs.groupBy { it.first }
                for ((surah, refs) in bySurah) {
                    val surahVerses = quranRepository.getVerses(surah, settings.arabicScript, settings.enabledTranslations)
                    val ayahSet = refs.map { it.second }.toSet()
                    allVerses.addAll(surahVerses.filter { it.ayah in ayahSet })
                }

                _uiState.value = TopicDetailUiState(
                    topic = topic, verses = allVerses, surahCount = bySurah.size,
                    isLoading = false, fontSize = settings.fontSize,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
