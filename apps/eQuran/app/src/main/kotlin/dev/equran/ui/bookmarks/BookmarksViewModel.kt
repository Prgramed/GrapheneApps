package dev.equran.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.domain.model.AyahWithTranslations
import dev.equran.domain.model.Bookmark
import dev.equran.domain.repository.BookmarkRepository
import dev.equran.domain.repository.QuranRepository
import dev.equran.data.preferences.QuranPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkWithVerse(
    val bookmark: Bookmark,
    val ayah: AyahWithTranslations?,
    val surahName: String,
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val quranRepository: QuranRepository,
    private val preferencesRepository: QuranPreferencesRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<BookmarkWithVerse>>(emptyList())
    val items: StateFlow<List<BookmarkWithVerse>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeAll().collect { bookmarks ->
                _isLoading.value = true
                try {
                    val settings = preferencesRepository.settings.first()
                    val surahList = quranRepository.getSurahList()
                    val items = bookmarks.map { bm ->
                        val surahVerses = quranRepository.getVerses(bm.surah, settings.arabicScript, settings.enabledTranslations)
                        val ayah = surahVerses.find { it.ayah == bm.ayah }
                        val surahName = surahList.getOrNull(bm.surah - 1)?.englishName ?: "Surah ${bm.surah}"
                        BookmarkWithVerse(bm, ayah, surahName)
                    }
                    _items.value = items
                } catch (_: Exception) {
                    _items.value = emptyList()
                }
                _isLoading.value = false
            }
        }
    }

    fun removeBookmark(surah: Int, ayah: Int) {
        viewModelScope.launch { bookmarkRepository.remove(surah, ayah) }
    }
}
