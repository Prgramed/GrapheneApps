package dev.egallery.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var evictionScheduler: EvictionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed — scheduling eviction + starting camera watcher")
            // No periodic sync — sync is manual from Settings
            evictionScheduler.scheduleDaily()
            // CameraWatcher removed — sync handles new photos
        }
    }
}
