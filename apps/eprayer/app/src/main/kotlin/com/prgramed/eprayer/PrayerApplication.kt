package com.prgramed.eprayer

import android.app.Application
import com.prgramed.eprayer.data.notification.PrayerNotificationManager
import com.prgramed.eprayer.data.notification.PrayerStartupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrayerApplication : Application() {

    @Inject
    lateinit var notificationManager: PrayerNotificationManager

    @Inject
    lateinit var startupScheduler: PrayerStartupScheduler

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel()
        startupScheduler.scheduleOnStartup()
    }
}
