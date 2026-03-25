package dev.emusic.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.emusic.data.api.CredentialProvider
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.data.preferences.CredentialProviderImpl
import dev.emusic.data.preferences.CredentialStore
import dev.emusic.data.preferences.NetworkMonitor
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "emusic_preferences")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesProviderModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext context: Context): CredentialStore =
        CredentialStore(context)

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context,
        preferencesRepository: AppPreferencesRepository,
    ): NetworkMonitor = NetworkMonitor(context, preferencesRepository)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesBindingModule {

    @Binds
    @Singleton
    abstract fun bindCredentialProvider(impl: CredentialProviderImpl): CredentialProvider
}
