package com.grapheneapps.enotes.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.grapheneapps.enotes.data.db.dao.AttachmentDao
import com.grapheneapps.enotes.data.db.dao.FolderDao
import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.dao.NoteRevisionDao
import com.grapheneapps.enotes.data.db.entity.AttachmentEntity
import com.grapheneapps.enotes.data.db.entity.FolderEntity
import com.grapheneapps.enotes.data.db.entity.NoteEntity
import com.grapheneapps.enotes.data.db.entity.NoteFtsEntity
import com.grapheneapps.enotes.data.db.entity.NoteRevisionEntity

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        FolderEntity::class,
        AttachmentEntity::class,
        NoteRevisionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun noteRevisionDao(): NoteRevisionDao
}
