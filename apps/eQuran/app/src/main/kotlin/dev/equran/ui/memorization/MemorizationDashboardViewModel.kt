package dev.equran.ui.memorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.domain.model.MemorizedVerse
import dev.equran.domain.model.SurahMeta
import dev.equran.domain.repository.MemorizationRepository
import dev.equran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurahProgress(val meta: SurahMeta, val memorized: Int)

data class DashboardUiState(
    val totalMemorized: Int = 0,
    val surahsComplete: Int = 0,
    val percentage: Float = 0f,
    val surahProgress: List<SurahProgress> = emptyList(),
)

@HiltViewModel
class MemorizationDashboardViewModel @Inject constructor(
    private val memorizationRepository: MemorizationRepository,
    private val quranRepository: QuranRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val surahs = quranRepository.getSurahList()
            memorizationRepository.observeAll().collect { all ->
                val bySurah = all.groupBy { it.surah }
                val totalMemorized = all.size
                val surahsComplete = surahs.count { s ->
                    (bySurah[s.number]?.size ?: 0) >= s.numberOfAyahs
                }
                val progress = surahs.map { s ->
                    SurahProgress(s, bySurah[s.number]?.size ?: 0)
                }
                _uiState.value = DashboardUiState(
                    totalMemorized = totalMemorized,
                    surahsComplete = surahsComplete,
                    percentage = (totalMemorized / 6236f).coerceIn(0f, 1f),
                    surahProgress = progress,
                )
            }
        }
    }
}
