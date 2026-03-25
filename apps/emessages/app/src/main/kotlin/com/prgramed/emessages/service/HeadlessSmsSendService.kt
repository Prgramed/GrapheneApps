package com.prgramed.emessages.service

import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager

@Suppress("DEPRECATION")
class HeadlessSmsSendService : IntentService("HeadlessSmsSendService") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE") return

        val recipient = intent.data?.schemeSpecificPart ?: return
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
        if (body.isBlank()) return

        try {
            val smsManager = getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, body, null, null)
            }

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (_: Exception) {
        }
    }
}
