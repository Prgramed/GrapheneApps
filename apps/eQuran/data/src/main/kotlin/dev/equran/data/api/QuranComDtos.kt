package dev.equran.data.api

import kotlinx.serialization.Serializable

// --- Tafsir from quran.com ---
@Serializable
data class QuranComTafsirResponse(
    val tafsir: QuranComTafsirContent? = null,
)

@Serializable
data class QuranComTafsirContent(
    val text: String = "",
)

// --- Tafsir from alquran.cloud ---
@Serializable
data class AlQuranCloudResponse(
    val data: AlQuranCloudData? = null,
)

@Serializable
data class AlQuranCloudData(
    val text: String = "",
)

// --- Word-by-word from quran.com ---
@Serializable
data class WordByWordResponse(
    val verses: List<WbwVerse> = emptyList(),
)

@Serializable
data class WbwVerse(
    val verse_key: String = "",
    val words: List<WbwWord> = emptyList(),
)

@Serializable
data class WbwWord(
    val position: Int = 0,
    val text_uthmani: String = "",
    val translation: WbwTranslation? = null,
    val transliteration: WbwTransliteration? = null,
)

@Serializable
data class WbwTranslation(val text: String = "")

@Serializable
data class WbwTransliteration(val text: String = "")
