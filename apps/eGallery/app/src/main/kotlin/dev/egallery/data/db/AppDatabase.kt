package dev.egallery.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.egallery.data.db.dao.AlbumDao
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.MediaTagDao
import dev.egallery.data.db.dao.PersonDao
import dev.egallery.data.db.dao.TagDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.db.entity.AlbumEntity
import dev.egallery.data.db.entity.AlbumMediaEntity
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.data.db.entity.MediaTagEntity
import dev.egallery.data.db.entity.PersonEntity
import dev.egallery.data.db.entity.TagEntity
import dev.egallery.data.db.entity.UploadQueueEntity

@Database(
    entities = [
        MediaEntity::class,
        AlbumEntity::class,
        PersonEntity::class,
        UploadQueueEntity::class,
        AlbumMediaEntity::class,
        TagEntity::class,
        MediaTagEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun albumDao(): AlbumDao
    abstract fun personDao(): PersonDao
    abstract fun uploadQueueDao(): UploadQueueDao
    abstract fun albumMediaDao(): AlbumMediaDao
    abstract fun tagDao(): TagDao
    abstract fun mediaTagDao(): MediaTagDao
}
