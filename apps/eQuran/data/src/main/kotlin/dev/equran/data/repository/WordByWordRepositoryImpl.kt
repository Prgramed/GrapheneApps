package dev.equran.data.repository

import dev.equran.data.api.QuranComApi
import dev.equran.data.db.dao.WordByWordDao
import dev.equran.data.db.entity.WordByWordCacheEntity
import dev.equran.domain.model.WordInfo
import dev.equran.domain.repository.WordByWordRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@kotlinx.serialization.Serializable
private data class CachedWord(val position: Int, val arabic: String, val translation: String, val transliteration: String?)

@Singleton
class WordByWordRepositoryImpl @Inject constructor(
    private val wordByWordDao: WordByWordDao,
    private val quranComApi: QuranComApi,
) : WordByWordRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getWords(surah: Int, ayah: Int): List<WordInfo> {
        // Check cache
        val cached = wordByWordDao.get(surah, ayah)
        if (cached != null) {
            return try {
                json.decodeFromString<List<CachedWord>>(cached.wordsJson)
                    .map { WordInfo(it.position, it.arabic, it.translation, it.transliteration) }
            } catch (_: Exception) { emptyList() }
        }

        // Fetch entire surah and cache all verses
        return try {
            val response = quranComApi.getWordByWord(surah)
            for (verse in response.verses) {
                val parts = verse.verse_key.split(":")
                if (parts.size != 2) continue
                val vSurah = parts[0].toIntOrNull() ?: continue
                val vAyah = parts[1].toIntOrNull() ?: continue

                val words = verse.words.map { w ->
                    CachedWord(w.position, w.text_uthmani, w.translation?.text ?: "", w.transliteration?.text)
                }
                wordByWordDao.upsert(
                    WordByWordCacheEntity(surah = vSurah, ayah = vAyah, wordsJson = json.encodeToString(words)),
                )
            }

            // Return requested verse
            val target = response.verses.find { it.verse_key == "$surah:$ayah" }
            target?.words?.map { w ->
                WordInfo(w.position, w.text_uthmani, w.translation?.text ?: "", w.transliteration?.text)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch word-by-word for $surah:$ayah")
            emptyList()
        }
    }
}
