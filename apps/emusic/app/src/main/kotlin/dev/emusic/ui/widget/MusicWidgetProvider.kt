package dev.emusic.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import dev.emusic.R
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioNowPlayingBridge
import dev.emusic.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_UPDATE = "dev.emusic.WIDGET_UPDATE"
        const val ACTION_WIDGET_PLAY_PAUSE = "dev.emusic.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "dev.emusic.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "dev.emusic.WIDGET_PREV"
    }

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var radioNowPlayingBridge: RadioNowPlayingBridge
    @Inject lateinit var sessionToken: SessionToken

    private val scope = CoroutineScope(SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_WIDGET_UPDATE -> updateAllWidgets(context)
            ACTION_WIDGET_PLAY_PAUSE -> controlPlayback(context) { mc ->
                if (mc.isPlaying) mc.pause() else mc.play()
            }
            ACTION_WIDGET_NEXT -> controlPlayback(context) { mc ->
                mc.seekToNextMediaItem()
            }
            ACTION_WIDGET_PREV -> controlPlayback(context) { mc ->
                mc.seekToPreviousMediaItem()
            }
        }
    }

    private fun controlPlayback(context: Context, action: (MediaController) -> Unit) {
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                val mc = future.get()
                action(mc)
                mc.release()
            } catch (_: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, MusicWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val track = queueManager.currentTrack.value
        val isLiveStream = queueManager.isLiveStream.value
        val radioStation = radioNowPlayingBridge.currentStation.value

        val views = RemoteViews(context.packageName, R.layout.widget_player)

        // Set click on widget body → open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        // Set button PendingIntents
        views.setOnClickPendingIntent(R.id.widget_play_pause, makeBroadcastIntent(context, ACTION_WIDGET_PLAY_PAUSE, 1))
        views.setOnClickPendingIntent(R.id.widget_next, makeBroadcastIntent(context, ACTION_WIDGET_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_prev, makeBroadcastIntent(context, ACTION_WIDGET_PREV, 3))

        if (track != null) {
            views.setTextViewText(R.id.widget_title, track.title)
            views.setTextViewText(R.id.widget_artist, "${track.artist} \u2014 ${track.album}")

            // Load album art async
            loadAlbumArt(context, track.albumId, ids, appWidgetManager, views)
        } else if (isLiveStream && radioStation != null) {
            views.setTextViewText(R.id.widget_title, radioStation.name)
            views.setTextViewText(R.id.widget_artist, radioStation.country ?: "Live Radio")
        } else {
            views.setTextViewText(R.id.widget_title, "eMusic")
            views.setTextViewText(R.id.widget_artist, "Not playing")
        }

        // Detect play state for icon
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                val mc = future.get()
                val playIcon = if (mc.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                views.setImageViewResource(R.id.widget_play_pause, playIcon)

                // Update progress bar
                val duration = mc.duration
                if (duration > 0) {
                    val progress = (mc.currentPosition * 1000 / duration).toInt()
                    views.setProgressBar(R.id.widget_progress, 1000, progress, false)
                } else {
                    views.setProgressBar(R.id.widget_progress, 1000, 0, false)
                }

                mc.release()
            } catch (_: Exception) {
                views.setImageViewResource(R.id.widget_play_pause, android.R.drawable.ic_media_play)
            }
            appWidgetManager.updateAppWidget(ids, views)
        }, MoreExecutors.directExecutor())

        // Update immediately with current state (icon will update async)
        appWidgetManager.updateAppWidget(ids, views)
    }

    private fun loadAlbumArt(
        context: Context,
        albumId: String,
        widgetIds: IntArray,
        appWidgetManager: AppWidgetManager,
        views: RemoteViews,
    ) {
        scope.launch {
            try {
                val url = libraryRepository.getCoverArtUrl(albumId, 128)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(128)
                    .build()
                val result = coil3.SingletonImageLoader.get(context).execute(request)
                val bitmap: Bitmap? = try { result.image?.toBitmap() } catch (_: Exception) { null }
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_art, bitmap)
                    appWidgetManager.updateAppWidget(widgetIds, views)
                }
            } catch (_: Exception) { }
        }
    }

    private fun makeBroadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            component = ComponentName(context, MusicWidgetProvider::class.java)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
