package dev.equran.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranIndexApiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private var cachedUrl: String? = null
    private var cachedApi: QuranIndexApi? = null

    fun getApi(serverUrl: String): QuranIndexApi {
        if (serverUrl == cachedUrl && cachedApi != null) return cachedApi!!
        val baseUrl = serverUrl.trimEnd('/') + "/"
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QuranIndexApi::class.java)
        cachedUrl = serverUrl
        cachedApi = api
        return api
    }
}
