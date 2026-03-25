package dev.eweather.data.api

import dev.eweather.data.api.dto.RainViewerResponseDto
import retrofit2.http.GET

interface RainViewerService {

    @GET("public/weather-maps.json")
    suspend fun getFrameList(): RainViewerResponseDto

    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
    }
}
