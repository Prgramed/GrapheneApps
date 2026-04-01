package dev.equran.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.equran.data.db.EQuranDatabase
import dev.equran.data.db.dao.*
import dev.equran.data.repository.*
import dev.equran.domain.repository.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EQuranDatabase =
        Room.databaseBuilder(context, EQuranDatabase::class.java, "equran.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideBookmarkDao(db: EQuranDatabase) = db.bookmarkDao()
    @Provides fun provideMemorizationDao(db: EQuranDatabase) = db.memorizationDao()
    @Provides fun provideReadingPlanDao(db: EQuranDatabase) = db.readingPlanDao()
    @Provides fun provideWordByWordDao(db: EQuranDatabase) = db.wordByWordDao()
    @Provides fun provideTafsirDao(db: EQuranDatabase) = db.tafsirDao()
    @Provides fun provideTopicDao(db: EQuranDatabase) = db.topicDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindQuranRepository(impl: QuranRepositoryImpl): QuranRepository
    @Binds abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
    @Binds abstract fun bindMemorizationRepository(impl: MemorizationRepositoryImpl): MemorizationRepository
    @Binds abstract fun bindReadingPlanRepository(impl: ReadingPlanRepositoryImpl): ReadingPlanRepository
    @Binds abstract fun bindTopicRepository(impl: TopicRepositoryImpl): TopicRepository
    @Binds abstract fun bindTafsirRepository(impl: TafsirRepositoryImpl): TafsirRepository
    @Binds abstract fun bindWordByWordRepository(impl: WordByWordRepositoryImpl): WordByWordRepository
}
