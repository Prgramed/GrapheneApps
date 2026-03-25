package dev.ecalendar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.ecalendar.sync.SyncWorker
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ECalendarApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        SyncWorker.schedule(this)
        cleanRsvpCache()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = java.io.File(filesDir, "crash.log")
                logFile.appendText(
                    buildString {
                        appendLine("--- Crash at ${java.util.Date()} ---")
                        appendLine("Thread: ${thread.name}")
                        appendLine(throwable.stackTraceToString())
                        appendLine()
                    },
                )
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun cleanRsvpCache() {
        val rsvpDir = java.io.File(cacheDir, "rsvp")
        if (!rsvpDir.exists()) return
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        rsvpDir.listFiles()?.forEach { file ->
            if (file.lastModified() < sevenDaysAgo) {
                file.delete()
            }
        }
    }
}
