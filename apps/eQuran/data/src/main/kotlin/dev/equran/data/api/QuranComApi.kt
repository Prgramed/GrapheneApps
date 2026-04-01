package dev.equran.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface QuranComApi {
    @GET("api/v4/tafsirs/{tafsirId}/by_ayah/{verseKey}")
    suspend fun getTafsir(
        @Path("tafsirId") tafsirId: Int,
        @Path("verseKey") verseKey: String, // "surah:ayah"
    ): QuranComTafsirResponse

    @GET("api/v4/verses/by_chapter/{surah}")
    suspend fun getWordByWord(
        @Path("surah") surah: Int,
        @Query("language") language: String = "en",
        @Query("words") words: Boolean = true,
        @Query("word_fields") wordFields: String = "text_uthmani,translation",
        @Query("per_page") perPage: Int = 300,
    ): WordByWordResponse
}

interface AlQuranCloudApi {
    @GET("v1/ayah/{verseKey}/{edition}")
    suspend fun getTafsir(
        @Path("verseKey") verseKey: String, // "surah:ayah"
        @Path("edition") edition: String,
    ): AlQuranCloudResponse
}
