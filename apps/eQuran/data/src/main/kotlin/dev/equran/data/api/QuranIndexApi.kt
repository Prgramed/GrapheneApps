package dev.equran.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface QuranIndexApi {
    @GET("quran/api/semantic-search")
    suspend fun semanticSearch(
        @Query("q") query: String,
        @Query("k") k: Int = 20,
    ): SemanticSearchResponse
}

@Serializable
data class SemanticSearchResponse(
    val results: List<SemanticSearchResult> = emptyList(),
    val count: Int = 0,
    val query: String = "",
)

@Serializable
data class SemanticSearchResult(
    val number: Int = 0,
    val surah: Int = 0,
    val ayah: Int = 0,
    val surahName: String = "",
    val surahEnglishName: String = "",
    val arabicText: String = "",
    val matchedText: String = "",
    val translationText: String? = null,
    val score: Float? = null,
)
