package dev.ecalendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.entity.toDomain
import dev.ecalendar.ical.ICalParser
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class EventAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val eventDao: EventDao,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleForEvent(
        uid: String,
        instanceStart: Long,
        alarmMins: List<Int>,
        title: String,
        location: String?,
    ) {
        val now = System.currentTimeMillis()
        alarmMins.forEach { mins ->
            val triggerAt = instanceStart - mins * 60_000L
            if (triggerAt <= now) return@forEach

            val intent = buildReminderIntent(uid, instanceStart, title, location, mins)
            val requestCode = "$uid$instanceStart$mins".hashCode().absoluteValue
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            scheduleExact(triggerAt, pendingIntent, uid, mins)
        }
    }

    fun cancelForEvent(uid: String, instanceStart: Long, alarmMins: List<Int>) {
        alarmMins.forEach { mins ->
            val intent = Intent(context, EventAlarmReceiver::class.java).apply {
                action = EventAlarmReceiver.ACTION_EVENT_REMINDER
            }
            val requestCode = "$uid$instanceStart$mins".hashCode().absoluteValue
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
        // Also cancel any snooze alarm for this event
        val snoozeIntent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = EventAlarmReceiver.ACTION_EVENT_REMINDER
        }
        val snoozeRequestCode = "${uid}snooze$instanceStart".hashCode().absoluteValue
        val snoozePending = PendingIntent.getBroadcast(
            context, snoozeRequestCode, snoozeIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (snoozePending != null) {
            alarmManager.cancel(snoozePending)
        }
    }

    fun scheduleSnooze(
        uid: String,
        instanceStart: Long,
        title: String,
        location: String?,
        delayMins: Int = 10,
    ) {
        val triggerAt = System.currentTimeMillis() + delayMins * 60_000L
        val intent = buildReminderIntent(uid, instanceStart, title, location, 0)
        val requestCode = "${uid}snooze$instanceStart".hashCode().absoluteValue
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(triggerAt, pendingIntent, uid, 0)
        Timber.d("Snoozed alarm for $uid, fires in ${delayMins}min")
    }

    /**
     * Reschedules alarms for all future events in the next 30 days.
     * Should be called after sync completes and on boot.
     */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val futureEnd = now + thirtyDaysMs

        val events = eventDao.getFutureEvents(now, futureEnd)
        var scheduled = 0

        // Group by uid to fetch series once per event
        val eventsByUid = events.groupBy { it.uid }
        for ((uid, instances) in eventsByUid) {
            val series = eventDao.getSeriesByUid(uid) ?: continue
            val alarms = try {
                ICalParser.parseAlarms(series.rawIcs).map { it.offsetMins }
            } catch (_: Exception) {
                continue
            }
            if (alarms.isEmpty()) continue

            for (event in instances) {
                val domain = event.toDomain()
                scheduleForEvent(
                    uid = domain.uid,
                    instanceStart = domain.instanceStart,
                    alarmMins = alarms,
                    title = domain.title,
                    location = domain.location,
                )
                scheduled++
                if (scheduled % 50 == 0) kotlinx.coroutines.yield()
            }
        }
        Timber.d("rescheduleAll: scheduled alarms for $scheduled events")
    }

    private fun buildReminderIntent(
        uid: String, instanceStart: Long, title: String, location: String?, offsetMins: Int,
    ) = Intent(context, EventAlarmReceiver::class.java).apply {
        action = EventAlarmReceiver.ACTION_EVENT_REMINDER
        putExtra(EventAlarmReceiver.EXTRA_UID, uid)
        putExtra(EventAlarmReceiver.EXTRA_INSTANCE_START, instanceStart)
        putExtra(EventAlarmReceiver.EXTRA_TITLE, title)
        putExtra(EventAlarmReceiver.EXTRA_LOCATION, location)
        putExtra(EventAlarmReceiver.EXTRA_OFFSET_MINS, offsetMins)
    }

    private fun scheduleExact(triggerAt: Long, pendingIntent: PendingIntent, uid: String, mins: Int) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Timber.d("Scheduled alarm for $uid at $triggerAt ($mins min before)")
            } catch (_: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Timber.w("Exact alarm not permitted, using inexact for $uid")
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            Timber.d("Scheduled inexact alarm for $uid (exact not permitted)")
        }
    }
}
