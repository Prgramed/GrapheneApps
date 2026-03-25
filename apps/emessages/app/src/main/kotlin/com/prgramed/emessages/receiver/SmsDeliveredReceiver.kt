package com.prgramed.emessages.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

class SmsDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        if (resultCode == Activity.RESULT_OK) {
            val values = ContentValues().apply {
                put(Telephony.Sms.STATUS, 1) // Delivered
            }
            try {
                val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
                context.contentResolver.update(uri, values, null, null)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
