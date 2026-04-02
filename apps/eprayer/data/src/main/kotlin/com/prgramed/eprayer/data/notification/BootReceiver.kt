package com.prgramed.eprayer.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prgramed.eprayer.domain.usecase.SchedulePrayerNotificationsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                val javaToday = java.time.LocalDate.now()
                val today = kotlinx.datetime.LocalDate(
                    javaToday.year, javaToday.monthValue, javaToday.dayOfMonth,
                )
                schedulePrayerNotificationsUseCase(today)
                // Also schedule tomorrow in case boot happens after Isha
                val javaTomorrow = javaToday.plusDays(1)
                val tomorrow = kotlinx.datetime.LocalDate(
                    javaTomorrow.year, javaTomorrow.monthValue, javaTomorrow.dayOfMonth,
                )
                schedulePrayerNotificationsUseCase(tomorrow)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
