package dev.emusic.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.emusic.domain.model.Track
import dev.emusic.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor() {

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent playback controls"
        }

        val headsUp = NotificationChannel(
            CHANNEL_HEADS_UP,
            "Track Changes",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Brief notification when track changes"
        }

        val download = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Download progress and completion"
        }

        val sleepTimer = NotificationChannel(
            CHANNEL_SLEEP_TIMER,
            "Sleep Timer",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Sleep timer expiry notification"
        }

        manager.createNotificationChannels(listOf(playback, headsUp, download, sleepTimer))
    }

    fun fireHeadsUpNotification(context: Context, track: Track) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Respect DND
        if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_HEADS_UP)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText(track.album)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(HEADS_UP_NOTIFICATION_ID, notification)
    }

    fun fireSleepTimerExpiredNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SLEEP_TIMER)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("eMusic stopped")
            .setContentText("Sleep timer ended")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()

        manager.notify(SLEEP_TIMER_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_PLAYBACK = "emusic_playback"
        const val CHANNEL_HEADS_UP = "emusic_headsup"
        const val CHANNEL_DOWNLOAD = "emusic_download"
        const val CHANNEL_SLEEP_TIMER = "emusic_sleep_timer"
        private const val HEADS_UP_NOTIFICATION_ID = 2000
        private const val SLEEP_TIMER_NOTIFICATION_ID = 2001
    }
}
