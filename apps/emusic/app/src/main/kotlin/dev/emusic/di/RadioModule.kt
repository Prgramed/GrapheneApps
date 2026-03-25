package dev.emusic.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.emusic.data.radio.RadioBrowserApiService
import dev.emusic.data.repository.InternetRadioRepositoryImpl
import dev.emusic.domain.repository.InternetRadioRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RadioProviderModule {

    @Provides
    @Singleton
    @Named("radioBrowser")
    fun provideRadioBrowserRetrofit(json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://de1.api.radio-browser.info/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideRadioBrowserApiService(
        @Named("radioBrowser") retrofit: Retrofit,
    ): RadioBrowserApiService =
        retrofit.create(RadioBrowserApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RadioBindingModule {

    @Binds
    @Singleton
    abstract fun bindInternetRadioRepository(impl: InternetRadioRepositoryImpl): InternetRadioRepository
}
