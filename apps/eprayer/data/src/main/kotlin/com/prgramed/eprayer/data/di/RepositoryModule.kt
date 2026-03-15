package com.prgramed.eprayer.data.di

import com.prgramed.eprayer.data.location.LocationRepositoryImpl
import com.prgramed.eprayer.data.notification.PrayerAlarmScheduler
import com.prgramed.eprayer.data.prayer.PrayerTimesRepositoryImpl
import com.prgramed.eprayer.data.preferences.UserPreferencesDataStore
import com.prgramed.eprayer.data.qibla.QiblaRepositoryImpl
import com.prgramed.eprayer.domain.repository.LocationRepository
import com.prgramed.eprayer.domain.repository.PrayerTimesRepository
import com.prgramed.eprayer.domain.repository.QiblaRepository
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import com.prgramed.eprayer.domain.scheduler.PrayerScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesDataStore,
    ): UserPreferencesRepository

    @Binds
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository

    @Binds
    abstract fun bindPrayerTimesRepository(
        impl: PrayerTimesRepositoryImpl,
    ): PrayerTimesRepository

    @Binds
    abstract fun bindQiblaRepository(
        impl: QiblaRepositoryImpl,
    ): QiblaRepository

    @Binds
    abstract fun bindPrayerScheduler(
        impl: PrayerAlarmScheduler,
    ): PrayerScheduler
}
