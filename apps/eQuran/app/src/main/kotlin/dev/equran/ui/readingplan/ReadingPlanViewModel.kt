package dev.equran.ui.readingplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equran.data.repository.getDailyAssignment
import dev.equran.data.repository.getDayNumber
import dev.equran.data.repository.getVersesPerDay
import dev.equran.domain.model.DailyAssignment
import dev.equran.domain.model.ReadingPlan
import dev.equran.domain.model.SurahMeta
import dev.equran.domain.repository.QuranRepository
import dev.equran.domain.repository.ReadingPlanRepository
import dev.equran.domain.repository.ReadingPlanStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadingPlanUiState(
    val plan: ReadingPlan? = null,
    val stats: ReadingPlanStats? = null,
    val assignment: DailyAssignment? = null,
    val dayNumber: Int = 0,
    val daysRemaining: Int = 0,
    val progressPct: Int = 0,
    val streak: Int = 0,
    val readDates: Set<String> = emptySet(),
    val isFinished: Boolean = false,
    val surahs: List<SurahMeta> = emptyList(),
    val selectedDays: Int = 30,
    val planName: String = "Khatma",
)

@HiltViewModel
class ReadingPlanViewModel @Inject constructor(
    private val readingPlanRepository: ReadingPlanRepository,
    private val quranRepository: QuranRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingPlanUiState())
    val uiState: StateFlow<ReadingPlanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val surahs = quranRepository.getSurahList()
            _uiState.value = _uiState.value.copy(surahs = surahs)

            readingPlanRepository.observeActivePlan().collect { plan ->
                if (plan != null) {
                    val stats = readingPlanRepository.getStats(plan.id)
                    val today = java.time.LocalDate.now().toString()
                    val dayNumber = getDayNumber(plan.startDate, today)
                    val isFinished = dayNumber > plan.totalDays
                    val clampedDay = dayNumber.coerceIn(1, plan.totalDays)
                    val assignment = getDailyAssignment(plan.totalDays, clampedDay, surahs)
                    val daysRemaining = (plan.totalDays - dayNumber + 1).coerceAtLeast(0)
                    val progressPct = if (stats.versesRead > 0) ((stats.versesRead / 6236f) * 100).toInt().coerceAtMost(100) else 0

                    // Streak: count consecutive days ending today
                    val sortedDates = stats.readDates.sorted().reversed()
                    var streak = 0
                    for (i in sortedDates.indices) {
                        val expected = java.time.LocalDate.now().minusDays(i.toLong()).toString()
                        if (i < sortedDates.size && sortedDates[i] == expected) streak++ else break
                    }

                    _uiState.value = _uiState.value.copy(
                        plan = plan, stats = stats, assignment = assignment,
                        dayNumber = dayNumber, daysRemaining = daysRemaining,
                        progressPct = progressPct, isFinished = isFinished,
                        streak = streak, readDates = stats.readDates.toSet(),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(plan = null, stats = null, assignment = null)
                }
            }
        }
    }

    fun setSelectedDays(days: Int) {
        _uiState.value = _uiState.value.copy(selectedDays = days)
    }

    fun setPlanName(name: String) {
        _uiState.value = _uiState.value.copy(planName = name)
    }

    fun createPlan() {
        val state = _uiState.value
        viewModelScope.launch {
            readingPlanRepository.createPlan(state.planName, state.selectedDays)
        }
    }

    fun archivePlan() {
        val planId = _uiState.value.plan?.id ?: return
        viewModelScope.launch { readingPlanRepository.archivePlan(planId) }
    }

    fun markTodayRead() {
        val plan = _uiState.value.plan ?: return
        val assignment = _uiState.value.assignment ?: return
        val surahs = _uiState.value.surahs
        val today = java.time.LocalDate.now().toString()

        viewModelScope.launch {
            val verses = mutableListOf<Pair<Int, Int>>()
            // Collect all verses in today's assignment range
            for (surah in surahs) {
                if (surah.number < assignment.startSurah || surah.number > assignment.endSurah) continue
                val startAyah = if (surah.number == assignment.startSurah) assignment.startAyah else 1
                val endAyah = if (surah.number == assignment.endSurah) assignment.endAyah else surah.numberOfAyahs
                for (ayah in startAyah..endAyah) {
                    verses.add(surah.number to ayah)
                }
            }
            readingPlanRepository.markRead(plan.id, verses, today)
            // Refresh stats
            val stats = readingPlanRepository.getStats(plan.id)
            _uiState.value = _uiState.value.copy(
                stats = stats,
                progressPct = ((stats.versesRead / 6236f) * 100).toInt().coerceAtMost(100),
            )
        }
    }

    fun getSurahName(surahNumber: Int): String {
        return _uiState.value.surahs.getOrNull(surahNumber - 1)?.englishName ?: "Surah $surahNumber"
    }
}
