package dev.egallery.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.egallery.data.repository.AlbumRepository
import dev.egallery.data.repository.AlbumRepositoryImpl
import dev.egallery.data.repository.MediaRepository
import dev.egallery.data.repository.MediaRepositoryImpl
import dev.egallery.data.repository.PersonRepository
import dev.egallery.data.repository.PersonRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository
}
