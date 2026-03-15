package com.prgramed.eprayer.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prgramed.eprayer.domain.usecase.SchedulePrayerNotificationsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var schedulePrayerNotificationsUseCase: SchedulePrayerNotificationsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = kotlin.time.Instant.fromEpochMilliseconds(
                    java.lang.System.currentTimeMillis(),
                )
                val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                schedulePrayerNotificationsUseCase(today)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
