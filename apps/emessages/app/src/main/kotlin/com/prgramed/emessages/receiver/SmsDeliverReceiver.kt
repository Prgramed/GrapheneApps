package com.prgramed.emessages.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import android.app.PendingIntent
import android.provider.BlockedNumberContract

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.getDefaultSmsSubscriptionId(),
        )

        // Group message parts by sender (multipart SMS)
        val grouped = mutableMapOf<String, StringBuilder>()
        var timestamp = System.currentTimeMillis()

        for (message in messages) {
            val address = message.originatingAddress ?: continue
            grouped.getOrPut(address) { StringBuilder() }.append(message.messageBody ?: "")
            timestamp = message.timestampMillis
        }

        for ((address, bodyBuilder) in grouped) {
            if (address.isBlank()) continue
            val body = bodyBuilder.toString()

            // Check if number is blocked
            try {
                if (BlockedNumberContract.isBlocked(context, address)) continue
            } catch (_: Exception) {
            }

            // Write to SMS provider
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
            try {
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (_: Exception) {
            }

            // Get thread ID for consistent notification IDs
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, address)
            } catch (_: Exception) {
                address.hashCode().toLong()
            }

            // Show notification
            showNotification(context, address, body, threadId)
        }
    }

    private fun showNotification(context: Context, address: String, body: String, threadId: Long) {
        // Ensure channel exists
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Look up contact name
        val senderName = lookupContactName(context, address) ?: address

        val person = Person.Builder().setName(senderName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .addMessage(body, System.currentTimeMillis(), person)

        // Tap to open app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            context, threadId.toInt(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Mark-as-read action
        val markReadIntent = Intent("com.prgramed.emessages.ACTION_MARK_READ").apply {
            setPackage(context.packageName)
            putExtra("extra_thread_id", threadId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, threadId.toInt() + 10000, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Reply action
        val remoteInput = androidx.core.app.RemoteInput.Builder("key_text_reply")
            .setLabel("Reply")
            .build()
        val replyIntent = Intent("com.prgramed.emessages.ACTION_REPLY").apply {
            setPackage(context.packageName)
            putExtra("extra_thread_id", threadId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, threadId.toInt(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(messagingStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_view, "Mark as read", markReadPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            // Use threadId.toInt() so cancelNotification(threadId) works
            NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun lookupContactName(context: Context, phoneNumber: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CHANNEL_ID = "messages"
    }
}
