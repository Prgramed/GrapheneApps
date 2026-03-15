package com.prgramed.eprayer

import android.app.Application
import com.prgramed.eprayer.data.notification.PrayerNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrayerApplication : Application() {

    @Inject
    lateinit var notificationManager: PrayerNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel()
    }
}
