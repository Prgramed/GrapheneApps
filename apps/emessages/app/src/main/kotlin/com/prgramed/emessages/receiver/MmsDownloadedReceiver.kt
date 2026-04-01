package com.prgramed.emessages.receiver

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import java.io.File

class MmsDownloadedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val resultCode = resultCode
        val filePath = intent.getStringExtra("file_path") ?: return
        val subscriptionId = intent.getIntExtra("sub_id", -1)
        val file = File(filePath)

        if (resultCode != Activity.RESULT_OK || !file.exists() || file.length() == 0L) {
            file.delete()
            // Store a placeholder MMS with content_location so user can retry download
            val contentLoc = intent.getStringExtra("content_location")
            val subscriptionId = intent.getIntExtra("sub_id", -1)
            if (contentLoc != null) {
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                        put(Telephony.Mms.READ, 0)
                        put(Telephony.Mms.SEEN, 0)
                        put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                        put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                        put(Telephony.Mms.CONTENT_LOCATION, contentLoc)
                        if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                    }
                    context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
                } catch (_: Exception) {}
            }
            return
        }

        if (file.length() > 10 * 1024 * 1024) {
            android.util.Log.w("MmsDownload", "MMS PDU too large (${file.length()} bytes), skipping")
            file.delete()
            return
        }
        val pduData = file.readBytes()
        file.delete()

        // Import MMS into ContentProvider using framework reflection
        val mmsUri = importMmsViaReflection(context, pduData, subscriptionId)
        if (mmsUri == null) return

        val mmsId = mmsUri.lastPathSegment?.toLongOrNull() ?: return

        // Get sender and body for notification
        val sender = getMmsSender(context, mmsId)
        val body = getMmsBody(context, mmsId) ?: "MMS received"

        // Pre-cache image thumbnails for smooth scrolling
        try {
            val partUri = android.net.Uri.parse("content://mms/part")
            context.contentResolver.query(
                partUri, arrayOf("_id", "ct"),
                "mid = ?", arrayOf(mmsId.toString()), null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val ctIdx = cursor.getColumnIndex("ct")
                while (cursor.moveToNext()) {
                    val ct = cursor.getString(ctIdx) ?: continue
                    if (ct.startsWith("image/")) {
                        val partId = cursor.getLong(idIdx)
                        val contentUri = android.net.Uri.parse("content://mms/part/$partId")
                        com.prgramed.emessages.data.message.ThumbnailCache.cache(context, contentUri, mmsId)
                    }
                }
            }
        } catch (_: Exception) {}

        if (sender != null) {
            val threadId = getThreadId(context, mmsId)
            showNotification(context, sender, body, threadId ?: sender.hashCode().toLong())
        }
    }

    @Suppress("DEPRECATION")
    private fun importMmsViaReflection(
        context: Context,
        pduData: ByteArray,
        subscriptionId: Int,
    ): Uri? {
        // Try reflection first (works on GMS devices)
        val uri = try {
            val parserClass = Class.forName("com.google.android.mms.pdu.PduParser")
            val parser = try {
                val ctor = parserClass.getConstructor(
                    ByteArray::class.java, Boolean::class.javaPrimitiveType,
                )
                ctor.newInstance(pduData, false)
            } catch (_: NoSuchMethodException) {
                parserClass.getConstructor(ByteArray::class.java).newInstance(pduData)
            }

            val parseMethod = parserClass.getMethod("parse")
            val pdu = parseMethod.invoke(parser) ?: return null

            val persisterClass = Class.forName("com.google.android.mms.pdu.PduPersister")
            val persister = persisterClass
                .getMethod("getPduPersister", Context::class.java)
                .invoke(null, context)

            val genericPduClass = Class.forName("com.google.android.mms.pdu.GenericPdu")
            val persistMethod = persisterClass.getMethod(
                "persist",
                genericPduClass,
                Uri::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                java.util.HashMap::class.java,
            )

            persistMethod.invoke(
                persister, pdu, Telephony.Mms.Inbox.CONTENT_URI,
                true, false, null,
            ) as? Uri
        } catch (e: Exception) {
            android.util.Log.w("MmsDownload", "Reflection import failed (expected on GrapheneOS): ${e.message}")
            // Fallback: write raw PDU to content provider (GrapheneOS/AOSP)
            try {
                val values = ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.SEEN, 0)
                    put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                    put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                    if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                }
                context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
            } catch (e2: Exception) {
                android.util.Log.e("MmsDownload", "Fallback import also failed", e2)
                null
            }
        }

        if (uri != null) {
            val values = ContentValues().apply {
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            }
            context.contentResolver.update(uri, values, null, null)
        }

        return uri
    }

    private fun getMmsSender(context: Context, mmsId: Long): String? = try {
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address"),
            "type = 137", null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() && it != "insert-address-token" }
            } else null
        }
    } catch (_: Exception) {
        null
    }

    private fun getMmsBody(context: Context, mmsId: Long): String? = try {
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf("ct", "text"),
            "ct = 'text/plain'", null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(1)?.takeIf { it.isNotBlank() } else null
        }
    } catch (_: Exception) {
        null
    }

    private fun getThreadId(context: Context, mmsId: Long): Long? = try {
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId"),
            arrayOf(Telephony.Mms.THREAD_ID),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0 } else null
        }
    } catch (_: Exception) {
        null
    }

    private fun showNotification(context: Context, sender: String, body: String, threadId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    enableLights(true)
                },
            )
        }

        val senderName = lookupContactName(context, sender) ?: sender
        val person = Person.Builder().setName(senderName).build()
        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(body, System.currentTimeMillis(), person)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, threadId.toInt(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun lookupContactName(context: Context, phoneNumber: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber),
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
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
