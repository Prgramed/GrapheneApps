package com.prgramed.emessages.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony

class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        val values = ContentValues().apply {
            if (resultCode == Activity.RESULT_OK) {
                put(Telephony.Sms.STATUS, 0) // Sent
            } else {
                put(Telephony.Sms.STATUS, 64) // Failed
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            }
        }

        try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
