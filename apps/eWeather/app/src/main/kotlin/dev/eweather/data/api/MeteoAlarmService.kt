package dev.eweather.data.api

import retrofit2.http.GET
import retrofit2.http.Path

interface MeteoAlarmService {

    @GET("feeds/{slug}")
    suspend fun getFeed(@Path("slug") slug: String): String

    companion object {
        const val BASE_URL = "https://feeds.meteoalarm.org/"
    }
}
