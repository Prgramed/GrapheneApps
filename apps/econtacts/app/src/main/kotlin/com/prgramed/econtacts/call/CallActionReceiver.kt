package com.prgramed.econtacts.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answer()
            ACTION_HANGUP -> CallManager.hangup()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.prgramed.econtacts.ACTION_ANSWER"
        const val ACTION_HANGUP = "com.prgramed.econtacts.ACTION_HANGUP"
    }
}
