package dev.equran.data.repository

import dev.equran.domain.model.DailyAssignment
import dev.equran.domain.model.SurahMeta
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val TOTAL_VERSES = 6236

fun getDailyAssignment(totalDays: Int, dayNumber: Int, surahs: List<SurahMeta>): DailyAssignment {
    val versesPerDay = ceil(TOTAL_VERSES.toDouble() / totalDays).toInt()
    val startIndex = (dayNumber - 1) * versesPerDay
    val endIndex = min(dayNumber * versesPerDay - 1, TOTAL_VERSES - 1)
    val versesCount = endIndex - startIndex + 1

    val start = globalIndexToSurahAyah(startIndex, surahs)
    val end = globalIndexToSurahAyah(endIndex, surahs)

    return DailyAssignment(
        dayNumber = dayNumber,
        startSurah = start.first,
        startAyah = start.second,
        endSurah = end.first,
        endAyah = end.second,
        versesCount = versesCount,
    )
}

fun getDayNumber(startDate: String, today: String): Int {
    return try {
        val start = java.time.LocalDate.parse(startDate)
        val current = java.time.LocalDate.parse(today)
        val diff = java.time.temporal.ChronoUnit.DAYS.between(start, current)
        (diff + 1).toInt()
    } catch (_: Exception) { 1 }
}

fun getVersesPerDay(totalDays: Int): Int = ceil(TOTAL_VERSES.toDouble() / totalDays).toInt()

private fun globalIndexToSurahAyah(index: Int, surahs: List<SurahMeta>): Pair<Int, Int> {
    if (surahs.isEmpty()) return 1 to 1
    var remaining = index
    for (s in surahs) {
        if (remaining < s.numberOfAyahs) {
            return s.number to (remaining + 1)
        }
        remaining -= s.numberOfAyahs
    }
    val last = surahs.last()
    return last.number to last.numberOfAyahs
}
