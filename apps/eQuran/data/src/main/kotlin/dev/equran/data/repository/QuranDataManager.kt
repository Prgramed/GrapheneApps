package dev.equran.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.equran.domain.model.AyahWithTranslations
import dev.equran.domain.model.SearchResult
import dev.equran.domain.model.SurahMeta
import dev.equran.domain.model.Translation
import dev.equran.domain.model.Verse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EditionJson(
    val edition: EditionInfo,
    val verses: List<VerseJson>,
)

@Serializable
data class EditionInfo(
    val identifier: String,
    val language: String,
    val name: String = "",
    val englishName: String = "",
    val direction: String = "ltr",
    val type: String = "translation",
)

@Serializable
data class VerseJson(
    val number: Int,
    val surah: Int,
    val ayah: Int,
    val text: String,
)

@Serializable
data class SurahMetaJson(
    val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val numberOfAyahs: Int,
    val revelationType: String,
)

@Singleton
class QuranDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val editionCache = object : LinkedHashMap<String, List<Verse>>(2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Verse>>?): Boolean = size > 2
    }
    private var surahMetaCache: List<SurahMeta>? = null

    fun getSurahList(): List<SurahMeta> {
        surahMetaCache?.let { return it }
        val raw = context.assets.open("metadata/surahs.json").bufferedReader().readText()
        val parsed = json.decodeFromString<List<SurahMetaJson>>(raw)
        val result = parsed.map {
            SurahMeta(it.number, it.name, it.englishName, it.englishNameTranslation, it.numberOfAyahs, it.revelationType)
        }
        surahMetaCache = result
        return result
    }

    fun getEditionVerses(editionId: String): List<Verse> {
        editionCache[editionId]?.let { return it }
        val filename = "quran/$editionId.json"
        return try {
            val raw = context.assets.open(filename).bufferedReader().readText()
            val parsed = json.decodeFromString<EditionJson>(raw)
            val verses = parsed.verses.map { Verse(it.number, it.surah, it.ayah, it.text) }
            // LRU: keep max 4 editions in memory
            if (editionCache.size >= 4) {
                val oldest = editionCache.keys.first()
                editionCache.remove(oldest)
            }
            editionCache[editionId] = verses
            verses
        } catch (e: Exception) {
            Timber.e(e, "Failed to load edition $editionId")
            emptyList()
        }
    }

    fun getVersesForSurah(surah: Int, script: String, translations: List<String>): List<AyahWithTranslations> {
        val arabicVerses = getEditionVerses(script).filter { it.surah == surah }
        val translationVerses = translations.map { editionId ->
            editionId to getEditionVerses(editionId).filter { it.surah == surah }
        }

        return arabicVerses.map { arabic ->
            val trans = translationVerses.mapNotNull { (editionId, verses) ->
                verses.find { it.ayah == arabic.ayah }?.let { Translation(editionId, it.text) }
            }
            // Strip Basmala from ayah 1 (the UI renders it separately as a header)
            val text = if (arabic.ayah == 1 && surah != 1 && surah != 9) {
                stripBasmala(arabic.text)
            } else arabic.text
            AyahWithTranslations(arabic.number, arabic.surah, arabic.ayah, text, trans)
        }
    }

    private fun stripBasmala(text: String): String {
        // Common Basmala prefixes in various Quran scripts
        val prefixes = listOf(
            "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ ",
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ",
            "بِسْمِ اللهِ الرَّحْمنِ الرَّحِيمِ ",
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) return text.removePrefix(prefix).trim()
        }
        return text
    }

    fun textSearch(query: String, limit: Int = 50): List<SearchResult> {
        val surahList = getSurahList()
        val q = query.lowercase().trim()
        if (q.length < 2) return emptyList()

        val results = mutableListOf<SearchResult>()

        // Search Arabic (normalize by removing diacritics)
        val arabicVerses = getEditionVerses("ar.quran-simple")
        for (verse in arabicVerses) {
            if (verse.text.contains(q) || verse.text.replace(Regex("[\\u064B-\\u065F\\u0670]"), "").contains(q)) {
                val meta = surahList.getOrNull(verse.surah - 1) ?: continue
                results.add(
                    SearchResult(verse.number, verse.surah, verse.ayah, meta.name, meta.englishName, verse.text, verse.text),
                )
                if (results.size >= limit) return results
            }
        }

        // Search English translation
        val englishVerses = getEditionVerses("en.sahih")
        val arabicUthmani = getEditionVerses("ar.quran-uthmani")
        for (verse in englishVerses) {
            if (verse.text.lowercase().contains(q)) {
                val meta = surahList.getOrNull(verse.surah - 1) ?: continue
                val arabic = arabicUthmani.find { it.number == verse.number }?.text ?: ""
                if (results.none { it.number == verse.number }) {
                    results.add(
                        SearchResult(verse.number, verse.surah, verse.ayah, meta.name, meta.englishName, arabic, verse.text),
                    )
                    if (results.size >= limit) return results
                }
            }
        }

        return results
    }
}
