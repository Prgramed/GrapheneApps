package com.grapheneapps.enotes.data.di

import android.content.Context
import androidx.room.Room
import com.grapheneapps.enotes.data.db.AppDatabase
// SQLCipher removed — per-note AES-256-GCM + GrapheneOS device encryption is sufficient
import com.grapheneapps.enotes.data.db.dao.AttachmentDao
import com.grapheneapps.enotes.data.db.dao.FolderDao
import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.dao.NoteRevisionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        // Currently at schema version 1.
        // ANY future schema change MUST ship a Migration — never add
        // fallbackToDestructiveMigration here, it would wipe all user notes.
        Room.databaseBuilder(context, AppDatabase::class.java, "enotes.db")
            .build()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideNoteRevisionDao(db: AppDatabase): NoteRevisionDao = db.noteRevisionDao()
}
