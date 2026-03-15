package com.prgramed.eprayer.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prgramed.eprayer.domain.model.Prayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: PrayerNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
        notificationManager.showPrayerNotification(prayer)
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    }
}
