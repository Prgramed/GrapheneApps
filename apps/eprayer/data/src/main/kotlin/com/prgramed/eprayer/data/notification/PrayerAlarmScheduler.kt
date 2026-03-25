package com.prgramed.eprayer.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.prgramed.eprayer.domain.model.Prayer
import com.prgramed.eprayer.domain.model.PrayerDay
import com.prgramed.eprayer.domain.scheduler.PrayerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PrayerScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleAlarms(prayerDay: PrayerDay, enabledPrayers: Set<String>) {
        cancelAllAlarms()

        val now = System.currentTimeMillis()

        prayerDay.times
            .filter { it.prayer != Prayer.SUNRISE }
            .filter { it.time.toEpochMilliseconds() > now }
            .filter { enabledPrayers.isEmpty() || it.prayer.name in enabledPrayers }
            .forEach { prayerTime ->
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayerTime.prayer.name)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    prayerTime.prayer.ordinal,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        prayerTime.time.toEpochMilliseconds(),
                        pendingIntent,
                    )
                } catch (_: SecurityException) {
                    // SCHEDULE_EXACT_ALARM not granted — fall back to inexact
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        prayerTime.time.toEpochMilliseconds(),
                        pendingIntent,
                    )
                }
            }
    }

    override fun cancelAllAlarms() {
        Prayer.entries
            .filter { it != Prayer.SUNRISE }
            .forEach { prayer ->
                val intent = Intent(context, PrayerAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    prayer.ordinal,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                }
            }
    }
}
