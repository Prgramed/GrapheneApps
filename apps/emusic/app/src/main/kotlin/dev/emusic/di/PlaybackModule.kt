package dev.emusic.di

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.emusic.playback.PlaybackService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideSessionToken(@ApplicationContext context: Context): SessionToken =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    @Provides
    fun provideMediaControllerFuture(
        @ApplicationContext context: Context,
        sessionToken: SessionToken,
    ): ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()
}
