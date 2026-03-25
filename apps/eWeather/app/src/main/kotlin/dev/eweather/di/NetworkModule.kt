package dev.eweather.di

import android.content.Context
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.eweather.data.api.OpenMeteoAirQualityService
import dev.eweather.data.api.OpenMeteoForecastService
import dev.eweather.data.api.MeteoAlarmService
import dev.eweather.data.api.OpenMeteoGeocodingService
import retrofit2.converter.scalars.ScalarsConverterFactory
import dev.eweather.data.api.RainViewerService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideJson(): Json = json

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("forecast")
    fun provideForecastRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoForecastService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("airQuality")
    fun provideAirQualityRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoAirQualityService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("geocoding")
    fun provideGeocodingRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoGeocodingService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("rainviewer")
    fun provideRainViewerRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(RainViewerService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideForecastService(@Named("forecast") retrofit: Retrofit): OpenMeteoForecastService =
        retrofit.create(OpenMeteoForecastService::class.java)

    @Provides
    @Singleton
    fun provideAirQualityService(@Named("airQuality") retrofit: Retrofit): OpenMeteoAirQualityService =
        retrofit.create(OpenMeteoAirQualityService::class.java)

    @Provides
    @Singleton
    fun provideGeocodingService(@Named("geocoding") retrofit: Retrofit): OpenMeteoGeocodingService =
        retrofit.create(OpenMeteoGeocodingService::class.java)

    @Provides
    @Singleton
    fun provideRainViewerService(@Named("rainviewer") retrofit: Retrofit): RainViewerService =
        retrofit.create(RainViewerService::class.java)

    @Provides
    @Singleton
    @Named("meteoalarm")
    fun provideMeteoAlarmRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(MeteoAlarmService.BASE_URL)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMeteoAlarmService(@Named("meteoalarm") retrofit: Retrofit): MeteoAlarmService =
        retrofit.create(MeteoAlarmService::class.java)
}
