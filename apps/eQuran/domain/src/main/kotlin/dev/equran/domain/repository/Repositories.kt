package dev.equran.domain.repository

import dev.equran.domain.model.*
import kotlinx.coroutines.flow.Flow

interface QuranRepository {
    suspend fun getSurahList(): List<SurahMeta>
    suspend fun getSurahMeta(surah: Int): SurahMeta?
    suspend fun getVerses(surah: Int, script: String, translations: List<String>): List<AyahWithTranslations>
    suspend fun textSearch(query: String, limit: Int = 50): List<SearchResult>
    suspend fun semanticSearch(query: String, k: Int = 20): List<SearchResult>
}

interface BookmarkRepository {
    fun observeAll(): Flow<List<Bookmark>>
    fun observeForSurah(surah: Int): Flow<List<Bookmark>>
    suspend fun add(surah: Int, ayah: Int, note: String? = null)
    suspend fun updateNote(surah: Int, ayah: Int, note: String?)
    suspend fun remove(surah: Int, ayah: Int)
    suspend fun isBookmarked(surah: Int, ayah: Int): Boolean
}

interface MemorizationRepository {
    fun observeAll(): Flow<List<MemorizedVerse>>
    fun observeForSurah(surah: Int): Flow<List<MemorizedVerse>>
    suspend fun markMemorized(surah: Int, ayah: Int, confidence: Int = 1)
    suspend fun removeMemorized(surah: Int, ayah: Int)
    suspend fun getReviewQueue(limit: Int = 20): List<MemorizedVerse>
    suspend fun updateReview(surah: Int, ayah: Int, confidence: Int)
}

interface ReadingPlanRepository {
    fun observeActivePlan(): Flow<ReadingPlan?>
    suspend fun createPlan(name: String, totalDays: Int)
    suspend fun archivePlan(planId: Long)
    suspend fun getProgress(planId: Long): List<ReadingProgress>
    suspend fun markRead(planId: Long, verses: List<Pair<Int, Int>>, date: String)
    suspend fun getStats(planId: Long): ReadingPlanStats
}

data class ReadingPlanStats(
    val versesRead: Int,
    val daysWithProgress: Int,
    val readDates: List<String> = emptyList(),
)

interface TopicRepository {
    suspend fun getAll(): List<Topic>
    suspend fun getTopicVerses(topicId: Long): List<Pair<Int, Int>> // surah, ayah
}

interface TafsirRepository {
    suspend fun getTafsir(surah: Int, ayah: Int, edition: String): String?
}

interface WordByWordRepository {
    suspend fun getWords(surah: Int, ayah: Int): List<WordInfo>
}
