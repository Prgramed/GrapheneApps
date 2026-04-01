package dev.equran.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.domain.model.AyahWithTranslations
import dev.equran.domain.model.SurahMeta
import dev.equran.domain.model.WordInfo
import dev.equran.domain.repository.BookmarkRepository
import dev.equran.domain.repository.MemorizationRepository
import dev.equran.domain.repository.QuranRepository
import dev.equran.domain.repository.TafsirRepository
import dev.equran.domain.repository.WordByWordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val surahMeta: SurahMeta? = null,
    val ayahs: List<AyahWithTranslations> = emptyList(),
    val isLoading: Boolean = true,
    val scrollToAyah: Int = 0,
    val fontSize: Float = 26f,
)

@HiltViewModel
class SurahReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val quranRepository: QuranRepository,
    private val preferencesRepository: QuranPreferencesRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val memorizationRepository: MemorizationRepository,
    private val tafsirRepository: TafsirRepository,
    private val wordByWordRepository: WordByWordRepository,
) : ViewModel() {

    val surahNumber: Int = savedStateHandle["surahNumber"] ?: 1
    private val scrollToAyah: Int = savedStateHandle["scrollToAyah"] ?: 0

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val bookmarkedAyahs = bookmarkRepository.observeForSurah(surahNumber)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memorizedAyahs = memorizationRepository.observeForSurah(surahNumber)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSurah()
        viewModelScope.launch {
            preferencesRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(fontSize = settings.fontSize)
                if (_uiState.value.ayahs.isNotEmpty()) {
                    val ayahs = quranRepository.getVerses(surahNumber, settings.arabicScript, settings.enabledTranslations)
                    _uiState.value = _uiState.value.copy(ayahs = ayahs)
                }
            }
        }
    }

    private fun loadSurah() {
        viewModelScope.launch {
            _uiState.value = ReaderUiState(isLoading = true)
            val settings = preferencesRepository.settings.first()
            val meta = quranRepository.getSurahMeta(surahNumber)
            val ayahs = quranRepository.getVerses(surahNumber, settings.arabicScript, settings.enabledTranslations)
            _uiState.value = ReaderUiState(
                surahMeta = meta, ayahs = ayahs, isLoading = false,
                scrollToAyah = scrollToAyah, fontSize = settings.fontSize,
            )
        }
    }

    fun toggleBookmark(surah: Int, ayah: Int) {
        viewModelScope.launch {
            if (bookmarkRepository.isBookmarked(surah, ayah)) {
                bookmarkRepository.remove(surah, ayah)
            } else {
                bookmarkRepository.add(surah, ayah)
            }
        }
    }

    fun updateBookmarkNote(surah: Int, ayah: Int, note: String) {
        viewModelScope.launch { bookmarkRepository.updateNote(surah, ayah, note) }
    }

    fun toggleMemorized(surah: Int, ayah: Int) {
        viewModelScope.launch {
            val existing = memorizedAyahs.value.find { it.ayah == ayah }
            if (existing != null) {
                memorizationRepository.removeMemorized(surah, ayah)
            } else {
                memorizationRepository.markMemorized(surah, ayah)
            }
        }
    }

    fun copyVerse(ayah: AyahWithTranslations) {
        val meta = _uiState.value.surahMeta ?: return
        val text = buildString {
            appendLine(ayah.arabicText)
            ayah.translations.forEach { appendLine(it.text) }
            append("— ${meta.englishName} ${ayah.surah}:${ayah.ayah}")
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Quran verse", text))
    }

    fun shareVerse(ayah: AyahWithTranslations) {
        val meta = _uiState.value.surahMeta ?: return
        val text = buildString {
            appendLine(ayah.arabicText)
            appendLine()
            ayah.translations.forEach { appendLine(it.text) }
            append("— ${meta.englishName} ${ayah.surah}:${ayah.ayah}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(Intent.createChooser(intent, "Share verse").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Tafsir
    private val _tafsirText = MutableStateFlow<String?>(null)
    val tafsirText: StateFlow<String?> = _tafsirText.asStateFlow()
    private val _tafsirLoading = MutableStateFlow(false)
    val tafsirLoading: StateFlow<Boolean> = _tafsirLoading.asStateFlow()

    fun loadTafsir(surah: Int, ayah: Int) {
        viewModelScope.launch {
            _tafsirLoading.value = true
            val settings = preferencesRepository.settings.first()
            _tafsirText.value = tafsirRepository.getTafsir(surah, ayah, settings.selectedTafsir)
            _tafsirLoading.value = false
        }
    }

    fun clearTafsir() { _tafsirText.value = null }

    // Word-by-word
    private val _words = MutableStateFlow<List<WordInfo>>(emptyList())
    val words: StateFlow<List<WordInfo>> = _words.asStateFlow()
    private val _wordsLoading = MutableStateFlow(false)
    val wordsLoading: StateFlow<Boolean> = _wordsLoading.asStateFlow()

    fun loadWordByWord(surah: Int, ayah: Int) {
        viewModelScope.launch {
            _wordsLoading.value = true
            _words.value = wordByWordRepository.getWords(surah, ayah)
            _wordsLoading.value = false
        }
    }

    fun clearWords() { _words.value = emptyList() }

    val hasPrevious: Boolean get() = surahNumber > 1
    val hasNext: Boolean get() = surahNumber < 114
    val previousSurah: Int get() = surahNumber - 1
    val nextSurah: Int get() = surahNumber + 1
}
