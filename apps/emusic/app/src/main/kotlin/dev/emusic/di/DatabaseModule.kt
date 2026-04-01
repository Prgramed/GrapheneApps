package dev.emusic.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.emusic.data.db.AppDatabase
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.CountryDao
import dev.emusic.data.db.dao.EqPresetDao
import dev.emusic.data.db.dao.LyricsDao
import dev.emusic.data.db.dao.RadioStationDao
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN coverArtId TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE albums ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "emusic.db")
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideArtistDao(db: AppDatabase): ArtistDao = db.artistDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideScrobbleDao(db: AppDatabase): ScrobbleDao = db.scrobbleDao()

    @Provides
    fun provideLyricsDao(db: AppDatabase): LyricsDao = db.lyricsDao()

    @Provides
    fun provideRadioStationDao(db: AppDatabase): RadioStationDao = db.radioStationDao()

    @Provides
    fun provideCountryDao(db: AppDatabase): CountryDao = db.countryDao()

    @Provides
    fun provideEqPresetDao(db: AppDatabase): EqPresetDao = db.eqPresetDao()
}
