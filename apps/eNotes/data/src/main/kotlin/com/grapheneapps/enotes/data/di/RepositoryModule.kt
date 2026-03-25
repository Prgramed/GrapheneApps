package com.grapheneapps.enotes.data.di

import com.grapheneapps.enotes.data.repository.FolderRepositoryImpl
import com.grapheneapps.enotes.data.repository.NoteRepositoryImpl
import com.grapheneapps.enotes.domain.repository.FolderRepository
import com.grapheneapps.enotes.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
