package dev.equran.data.repository

import dev.equran.data.db.dao.*
import dev.equran.data.db.entity.*
import dev.equran.data.api.QuranIndexApiProvider
import dev.equran.data.preferences.QuranPreferencesRepository
import dev.equran.domain.model.*
import dev.equran.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepositoryImpl @Inject constructor(
    private val dataManager: QuranDataManager,
    private val quranIndexApiProvider: QuranIndexApiProvider,
    private val preferencesRepository: QuranPreferencesRepository,
) : QuranRepository {
    override suspend fun getSurahList() = withContext(Dispatchers.IO) { dataManager.getSurahList() }
    override suspend fun getSurahMeta(surah: Int) = withContext(Dispatchers.IO) { dataManager.getSurahList().getOrNull(surah - 1) }
    override suspend fun getVerses(surah: Int, script: String, translations: List<String>) =
        withContext(Dispatchers.IO) { dataManager.getVersesForSurah(surah, script, translations) }
    override suspend fun textSearch(query: String, limit: Int) =
        withContext(Dispatchers.IO) { dataManager.textSearch(query, limit) }
    override suspend fun semanticSearch(query: String, k: Int): List<SearchResult> {
        val serverUrl = preferencesRepository.settings.first().quranIndexServerUrl
        if (serverUrl.isBlank()) return emptyList()
        return try {
            val api = quranIndexApiProvider.getApi(serverUrl)
            val response = api.semanticSearch(query, k)
            response.results.map { r ->
                SearchResult(r.number, r.surah, r.ayah, r.surahName, r.surahEnglishName, r.arabicText, r.matchedText, r.score)
            }
        } catch (e: Exception) {
            Timber.w(e, "Semantic search failed")
            emptyList()
        }
    }
}

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {
    override fun observeAll() = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeForSurah(surah: Int) = dao.observeForSurah(surah).map { list -> list.map { it.toDomain() } }
    override suspend fun add(surah: Int, ayah: Int, note: String?) { dao.upsert(BookmarkEntity(surah = surah, ayah = ayah, note = note)) }
    override suspend fun updateNote(surah: Int, ayah: Int, note: String?) { dao.updateNote(surah, ayah, note) }
    override suspend fun remove(surah: Int, ayah: Int) { dao.delete(surah, ayah) }
    override suspend fun isBookmarked(surah: Int, ayah: Int) = dao.exists(surah, ayah)
}

@Singleton
class MemorizationRepositoryImpl @Inject constructor(
    private val dao: MemorizationDao,
) : MemorizationRepository {
    override fun observeAll() = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeForSurah(surah: Int) = dao.observeForSurah(surah).map { list -> list.map { it.toDomain() } }
    override suspend fun markMemorized(surah: Int, ayah: Int, confidence: Int) {
        dao.upsert(MemorizedVerseEntity(surah = surah, ayah = ayah, confidence = confidence))
    }
    override suspend fun removeMemorized(surah: Int, ayah: Int) { dao.delete(surah, ayah) }
    override suspend fun getReviewQueue(limit: Int) = dao.getReviewQueue(limit).map { it.toDomain() }
    override suspend fun updateReview(surah: Int, ayah: Int, confidence: Int) { dao.updateReview(surah, ayah, confidence) }
}

@Singleton
class ReadingPlanRepositoryImpl @Inject constructor(
    private val dao: ReadingPlanDao,
) : ReadingPlanRepository {
    override fun observeActivePlan() = dao.observeActive().map { it?.toDomain() }
    override suspend fun createPlan(name: String, totalDays: Int) {
        val today = java.time.LocalDate.now().toString()
        dao.insert(ReadingPlanEntity(name = name, totalDays = totalDays, startDate = today))
    }
    override suspend fun archivePlan(planId: Long) { dao.archive(planId) }
    override suspend fun getProgress(planId: Long) = dao.getProgress(planId).map { ReadingProgress(it.surah, it.ayah, it.readDate) }
    override suspend fun markRead(planId: Long, verses: List<Pair<Int, Int>>, date: String) {
        dao.insertProgress(verses.map { (s, a) -> ReadingProgressEntity(planId = planId, surah = s, ayah = a, readDate = date) })
    }
    override suspend fun getStats(planId: Long) = ReadingPlanStats(
        versesRead = dao.getVersesReadCount(planId),
        daysWithProgress = dao.getDaysWithProgressCount(planId),
        readDates = dao.getReadDates(planId),
    )
}

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val dao: TopicDao,
) : TopicRepository {
    override suspend fun getAll() = dao.getAllWithCount().map {
        Topic(it.id, it.nameEn, it.nameAr, it.description, it.category, it.verseCount)
    }
    override suspend fun getTopicVerses(topicId: Long) = dao.getVerses(topicId).map { it.surah to it.ayah }
}

// Extension mappers
private fun BookmarkEntity.toDomain() = Bookmark(id, surah, ayah, note, createdAt)
private fun MemorizedVerseEntity.toDomain() = MemorizedVerse(surah, ayah, confidence, memorizedAt, lastReviewed)
private fun ReadingPlanEntity.toDomain() = ReadingPlan(id, name, totalDays, startDate, isActive)
