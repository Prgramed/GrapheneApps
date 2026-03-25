package dev.eweather.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.eweather.data.repository.AlertRepositoryImpl
import dev.eweather.data.repository.LocationRepositoryImpl
import dev.eweather.data.repository.WeatherRepositoryImpl
import dev.eweather.domain.repository.AlertRepository
import dev.eweather.domain.repository.LocationRepository
import dev.eweather.domain.repository.WeatherRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository
}
