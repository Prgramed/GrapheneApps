package dev.equran.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.equran.data.db.dao.*
import dev.equran.data.db.entity.*

@Database(
    entities = [
        BookmarkEntity::class,
        MemorizedVerseEntity::class,
        ReadingPlanEntity::class,
        ReadingProgressEntity::class,
        WordByWordCacheEntity::class,
        TafsirCacheEntity::class,
        TopicEntity::class,
        TopicVerseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EQuranDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun memorizationDao(): MemorizationDao
    abstract fun readingPlanDao(): ReadingPlanDao
    abstract fun wordByWordDao(): WordByWordDao
    abstract fun tafsirDao(): TafsirDao
    abstract fun topicDao(): TopicDao
}
