package dev.ecalendar.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.ecalendar.data.db.dao.AccountDao
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.dao.SyncQueueDao
import dev.ecalendar.data.db.entity.AccountEntity
import dev.ecalendar.data.db.entity.CalendarEventEntity
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import dev.ecalendar.data.db.entity.EventSeriesEntity
import dev.ecalendar.data.db.entity.SyncQueueEntity

@Database(
    entities = [
        AccountEntity::class,
        CalendarSourceEntity::class,
        EventSeriesEntity::class,
        CalendarEventEntity::class,
        SyncQueueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun calendarDao(): CalendarDao
    abstract fun accountDao(): AccountDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** For widget use — Hilt-managed code should NOT use this. */
        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecalendar.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
