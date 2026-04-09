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
    ): AppDatabase {
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Migrate storage status: ON_DEVICE → SYNCED (if real nasId) or DEVICE (if temp)
                db.execSQL("UPDATE media SET storageStatus = 'SYNCED' WHERE storageStatus = 'ON_DEVICE' AND length(nasId) > 10 AND nasId NOT LIKE '-%'")
                db.execSQL("UPDATE media SET storageStatus = 'DEVICE' WHERE storageStatus = 'ON_DEVICE'")
                db.execSQL("UPDATE media SET storageStatus = 'NAS' WHERE storageStatus = 'NAS_ONLY'")
                db.execSQL("UPDATE media SET storageStatus = 'DEVICE' WHERE storageStatus = 'UPLOAD_PENDING'")
                db.execSQL("UPDATE media SET storageStatus = 'DEVICE' WHERE storageStatus = 'UPLOAD_FAILED'")
                db.execSQL("DELETE FROM media WHERE storageStatus = 'TRASHED'")
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_storageStatus_captureDate` ON `media` (`storageStatus`, `captureDate`)")
            }
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, "egallery.db")
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides fun provideMediaDao(db: AppDatabase): MediaDao = db.mediaDao()
    @Provides fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
    @Provides fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()
    @Provides fun provideUploadQueueDao(db: AppDatabase): UploadQueueDao = db.uploadQueueDao()
    @Provides fun provideAlbumMediaDao(db: AppDatabase): AlbumMediaDao = db.albumMediaDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideMediaTagDao(db: AppDatabase): MediaTagDao = db.mediaTagDao()
}
