package com.prgramed.eprayer.data.di

import android.content.Context
import androidx.room.Room
import com.prgramed.eprayer.data.database.PrayerDatabase
import com.prgramed.eprayer.data.database.PrayerLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providePrayerDatabase(@ApplicationContext context: Context): PrayerDatabase =
        Room.databaseBuilder(context, PrayerDatabase::class.java, "eprayer.db")
            .build()

    @Provides
    fun providePrayerLogDao(database: PrayerDatabase): PrayerLogDao =
        database.prayerLogDao()
}
