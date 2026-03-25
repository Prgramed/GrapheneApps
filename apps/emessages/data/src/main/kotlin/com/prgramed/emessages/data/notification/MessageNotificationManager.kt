package com.prgramed.emessages.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New message notifications"
            enableVibration(true)
            enableLights(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        threadId: Long,
        senderName: String,
        body: String,
        senderPhotoUri: String?,
    ) {
        createNotificationChannel()

        val senderIcon = senderPhotoUri?.let { loadContactPhoto(it) }
        val person = Person.Builder()
            .setName(senderName)
            .apply { senderIcon?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(body, System.currentTimeMillis(), person)

        // Reply action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(ACTION_REPLY).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            threadId.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        // Mark-as-read action
        val markReadIntent = Intent(ACTION_MARK_READ).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            threadId.toInt() + 10000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            markReadPendingIntent,
        ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(messagingStyle)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    fun cancelNotification(threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }

    private fun loadContactPhoto(uriString: String): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
        ImageDecoder.decodeBitmap(source)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val CHANNEL_ID = "messages"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_REPLY = "com.prgramed.emessages.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.prgramed.emessages.ACTION_MARK_READ"
        const val EXTRA_THREAD_ID = "extra_thread_id"
    }
}
