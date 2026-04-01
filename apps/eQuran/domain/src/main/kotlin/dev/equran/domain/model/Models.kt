package dev.equran.domain.model

data class SurahMeta(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val numberOfAyahs: Int,
    val revelationType: String,
)

data class Verse(
    val number: Int,
    val surah: Int,
    val ayah: Int,
    val text: String,
)

data class AyahWithTranslations(
    val number: Int,
    val surah: Int,
    val ayah: Int,
    val arabicText: String,
    val translations: List<Translation>,
)

data class Translation(
    val edition: String,
    val text: String,
)

data class SearchResult(
    val number: Int,
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val surahEnglishName: String,
    val arabicText: String,
    val matchedText: String,
    val score: Float? = null,
)

data class WordInfo(
    val position: Int,
    val arabic: String,
    val translation: String,
    val transliteration: String?,
)

data class Topic(
    val id: Long,
    val nameEn: String,
    val nameAr: String?,
    val description: String?,
    val category: String,
    val verseCount: Int = 0,
)

data class Bookmark(
    val id: Long,
    val surah: Int,
    val ayah: Int,
    val note: String?,
    val createdAt: Long,
)

data class MemorizedVerse(
    val surah: Int,
    val ayah: Int,
    val confidence: Int,
    val memorizedAt: Long,
    val lastReviewed: Long?,
)

data class ReadingPlan(
    val id: Long,
    val name: String,
    val totalDays: Int,
    val startDate: String,
    val isActive: Boolean,
)

data class ReadingProgress(
    val surah: Int,
    val ayah: Int,
    val readDate: String,
)

data class DailyAssignment(
    val dayNumber: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val versesCount: Int,
)
