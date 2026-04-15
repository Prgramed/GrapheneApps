package dev.ecalendar.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ecalendar.data.db.AppDatabase
import dev.ecalendar.data.db.dao.AccountDao
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.dao.SyncQueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        // Any schema change MUST ship a Migration. Do NOT re-add
        // fallbackToDestructiveMigration here — it would silently wipe all
        // accounts, calendars and local events on upgrade.
        Room.databaseBuilder(context, AppDatabase::class.java, "ecalendar.db")
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideCalendarDao(db: AppDatabase): CalendarDao = db.calendarDao()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()
}
