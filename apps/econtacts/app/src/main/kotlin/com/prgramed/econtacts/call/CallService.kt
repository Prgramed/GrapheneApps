package com.prgramed.econtacts.call

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService

class CallService : InCallService() {

    private val notificationCallback = object : Call.Callback() {
        @Suppress("DEPRECATION")
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_DISCONNECTED) {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(call))
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        AudioRouteManager.setService(this)
        CallManager.addCall(call)

        // Pre-resolve caller info (Service has full permissions, works on lock screen)
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        if (number.isNotBlank()) {
            val resolved = resolveContactFull(contentResolver, number)
            CallManager.setCallerInfo(resolved?.first, resolved?.second)
        }

        call.registerCallback(notificationCallback)
        ensureChannels()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(call),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )
        CallActivity.start(this)
    }

    @Suppress("DEPRECATION")
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(notificationCallback)

        // Detect missed call before removing from manager
        val wasMissed = call.details?.disconnectCause?.code == DisconnectCause.MISSED
        if (wasMissed) {
            val number = call.details?.handle?.schemeSpecificPart ?: ""
            if (number.isNotBlank()) {
                showMissedCallNotification(number)
            }
        }

        CallManager.removeCall(call)
        if (CallManager.calls.value.isEmpty()) {
            AudioRouteManager.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        AudioRouteManager.updateAudioState(audioState)
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(call: Call): Notification {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val callerName = lookupContactName(contentResolver, number) ?: number
        val isRinging = call.state == Call.STATE_RINGING

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val hangUpIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_HANGUP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val caller = Person.Builder().setName(callerName).setImportant(true).build()

        if (isRinging) {
            // Incoming call — full-screen intent + heads-up on all screens
            val answerIntent = PendingIntent.getBroadcast(
                this, 2,
                Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_ANSWER
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val fullScreenIntent = PendingIntent.getActivity(
                this, 3,
                Intent(this, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(this, CHANNEL_RINGING)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentIntent(contentIntent)
                .setStyle(Notification.CallStyle.forIncomingCall(caller, hangUpIntent, answerIntent))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenIntent, true)
                .build()
        }

        // Ongoing call — visible notification with green call chip
        return Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setStyle(Notification.CallStyle.forOngoingCall(caller, hangUpIntent))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
    }

    private fun showMissedCallNotification(number: String) {
        ensureChannels()
        val callerName = lookupContactName(contentResolver, number) ?: number

        // Open eContacts when tapped
        val contentIntent = PendingIntent.getActivity(
            this, MISSED_NOTIFICATION_BASE + number.hashCode(),
            Intent(this, com.prgramed.econtacts.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Call back action
        val callBackIntent = PendingIntent.getActivity(
            this, MISSED_NOTIFICATION_BASE + number.hashCode() + 1,
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed call")
            .setContentText(callerName)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .addAction(
                Notification.Action.Builder(
                    null, "Call back", callBackIntent,
                ).build(),
            )
            .build()

        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(MISSED_NOTIFICATION_BASE + number.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("ongoing_call")
        nm.deleteNotificationChannel("incoming_call")
        nm.deleteNotificationChannel("call_silent")

        if (nm.getNotificationChannel(CHANNEL_RINGING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RINGING, "Incoming calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ONGOING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING, "Ongoing calls",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_MISSED) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MISSED, "Missed calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val MISSED_NOTIFICATION_BASE = 2000
        private const val CHANNEL_RINGING = "call_ringing"
        private const val CHANNEL_ONGOING = "call_ongoing"
        private const val CHANNEL_MISSED = "call_missed"

        private fun resolveContactFull(resolver: ContentResolver, number: String): Pair<String, String?>? = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number),
            )
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photo = cursor.getString(1)
                    if (name != null) Pair(name, photo) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }

        private fun lookupContactName(resolver: ContentResolver, number: String): String? = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number),
            )
            resolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
