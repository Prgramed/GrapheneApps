package dev.egallery.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.egallery.data.db.AppDatabase
import dev.egallery.data.db.dao.AlbumDao
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.MediaTagDao
import dev.egallery.data.db.dao.PersonDao
import dev.egallery.data.db.dao.TagDao
import dev.egallery.data.db.dao.UploadQueueDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "egallery.db",
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides fun provideMediaDao(db: AppDatabase): MediaDao = db.mediaDao()
    @Provides fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
    @Provides fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()
    @Provides fun provideUploadQueueDao(db: AppDatabase): UploadQueueDao = db.uploadQueueDao()
    @Provides fun provideAlbumMediaDao(db: AppDatabase): AlbumMediaDao = db.albumMediaDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideMediaTagDao(db: AppDatabase): MediaTagDao = db.mediaTagDao()
}
