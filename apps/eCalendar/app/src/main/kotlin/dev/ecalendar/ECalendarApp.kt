package dev.ecalendar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ECalendarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (dev.ecalendar.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
