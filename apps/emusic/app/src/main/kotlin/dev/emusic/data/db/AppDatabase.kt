package dev.emusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.CountryDao
import dev.emusic.data.db.dao.EqPresetDao
import dev.emusic.data.db.dao.LyricsDao
import dev.emusic.data.db.dao.RadioStationDao
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.AlbumEntity
import dev.emusic.data.db.entity.ArtistEntity
import dev.emusic.data.db.entity.PlaylistEntity
import dev.emusic.data.db.entity.PlaylistTrackCrossRef
import dev.emusic.data.db.entity.CountryEntity
import dev.emusic.data.db.entity.EqPresetEntity
import dev.emusic.data.db.entity.LyricsEntity
import dev.emusic.data.db.entity.RadioStationEntity
import dev.emusic.data.db.entity.ScrobbleEntity
import dev.emusic.data.db.entity.TrackEntity
import dev.emusic.data.db.entity.TrackFtsEntity

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        TrackFtsEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ScrobbleEntity::class,
        LyricsEntity::class,
        RadioStationEntity::class,
        CountryEntity::class,
        EqPresetEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun scrobbleDao(): ScrobbleDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun radioStationDao(): RadioStationDao
    abstract fun countryDao(): CountryDao
    abstract fun eqPresetDao(): EqPresetDao
}
