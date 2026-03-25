package com.prgramed.emessages.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.prgramed.emessages.data.notification.MessageNotificationManager

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotificationManager.EXTRA_THREAD_ID, -1)
        if (threadId == -1L) return

        when (intent.action) {
            MessageNotificationManager.ACTION_MARK_READ -> {
                val pendingResult = goAsync()
                Thread {
                    try {
                        // Mark all SMS in thread as read
                        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
                        context.contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            values,
                            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                            arrayOf(threadId.toString()),
                        )
                        // Cancel the notification
                        NotificationManagerCompat.from(context).cancel(threadId.toInt())
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
            MessageNotificationManager.ACTION_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput
                    ?.getCharSequence(MessageNotificationManager.KEY_TEXT_REPLY)
                    ?.toString()
                if (replyText.isNullOrBlank()) return

                val pendingResult = goAsync()
                Thread {
                    try {
                        // Find the address for this thread
                        val address = context.contentResolver.query(
                            Telephony.Sms.CONTENT_URI,
                            arrayOf(Telephony.Sms.ADDRESS),
                            "${Telephony.Sms.THREAD_ID} = ?",
                            arrayOf(threadId.toString()),
                            "${Telephony.Sms.DATE} DESC LIMIT 1",
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }

                        if (address != null) {
                            // Send the reply
                            val smsManager = android.telephony.SmsManager.getDefault()
                            val parts = smsManager.divideMessage(replyText)
                            if (parts.size == 1) {
                                smsManager.sendTextMessage(address, null, replyText, null, null)
                            } else {
                                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                            }

                            // Write to sent messages
                            val sentValues = ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, address)
                                put(Telephony.Sms.BODY, replyText)
                                put(Telephony.Sms.DATE, System.currentTimeMillis())
                                put(Telephony.Sms.READ, 1)
                                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                                put(Telephony.Sms.SEEN, 1)
                            }
                            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, sentValues)

                            // Mark thread as read and cancel notification
                            val readValues = ContentValues().apply { put(Telephony.Sms.READ, 1) }
                            context.contentResolver.update(
                                Telephony.Sms.CONTENT_URI,
                                readValues,
                                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                                arrayOf(threadId.toString()),
                            )
                            NotificationManagerCompat.from(context).cancel(threadId.toInt())
                        }
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
        }
    }
}
