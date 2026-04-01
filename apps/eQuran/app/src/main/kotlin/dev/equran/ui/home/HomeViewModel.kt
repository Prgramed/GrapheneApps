package dev.equran.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.domain.model.SurahMeta
import dev.equran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JuzInfo(val number: Int, val startSurah: Int, val startAyah: Int, val surahName: String)

// Standard Quran juz boundaries (surah, ayah)
private val JUZ_BOUNDARIES = listOf(
    1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 83, 6 to 111, 7 to 88, 8 to 41,
    9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
    29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
) : ViewModel() {

    private val _surahs = MutableStateFlow<List<SurahMeta>>(emptyList())
    val surahs: StateFlow<List<SurahMeta>> = _surahs.asStateFlow()

    private val _juzList = MutableStateFlow<List<JuzInfo>>(emptyList())
    val juzList: StateFlow<List<JuzInfo>> = _juzList.asStateFlow()

    private val _showJuz = MutableStateFlow(false)
    val showJuz: StateFlow<Boolean> = _showJuz.asStateFlow()

    init {
        viewModelScope.launch {
            val surahs = quranRepository.getSurahList()
            _surahs.value = surahs
            _juzList.value = JUZ_BOUNDARIES.mapIndexed { index, (surah, ayah) ->
                JuzInfo(index + 1, surah, ayah, surahs.getOrNull(surah - 1)?.englishName ?: "")
            }
        }
    }

    fun toggleView() {
        _showJuz.value = !_showJuz.value
    }
}
