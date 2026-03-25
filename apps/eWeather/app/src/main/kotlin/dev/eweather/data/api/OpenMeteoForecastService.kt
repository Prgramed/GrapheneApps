package dev.eweather.data.api

import dev.eweather.data.api.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoForecastService {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("forecast_days") forecastDays: Int = 10,
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
    ): ForecastResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        private const val CURRENT_PARAMS =
            "temperature_2m,apparent_temperature,weather_code,wind_speed_10m," +
                "wind_direction_10m,relative_humidity_2m,precipitation," +
                "surface_pressure,cloud_cover,visibility,is_day"

        private const val HOURLY_PARAMS =
            "temperature_2m,apparent_temperature,precipitation_probability," +
                "precipitation,weather_code,wind_speed_10m,wind_direction_10m," +
                "relative_humidity_2m,uv_index,is_day"

        private const val DAILY_PARAMS =
            "temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset," +
                "uv_index_max,precipitation_sum,wind_speed_10m_max," +
                "wind_direction_10m_dominant"
    }
}
