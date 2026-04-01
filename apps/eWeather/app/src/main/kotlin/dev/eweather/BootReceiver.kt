package dev.eweather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.eweather.data.worker.WeatherRefreshWorker
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed — scheduling weather refresh")
            WeatherRefreshWorker.schedule(context)
        }
    }
}
