package dev.eweather.data.api

import dev.eweather.data.api.dto.AirQualityResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoAirQualityService {

    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("forecast_days") forecastDays: Int = 1,
    ): AirQualityResponseDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"

        private const val HOURLY_PARAMS =
            "pm2_5,pm10,nitrogen_dioxide,ozone,european_aqi,us_aqi"
    }
}
