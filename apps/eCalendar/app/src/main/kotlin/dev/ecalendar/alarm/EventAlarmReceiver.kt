package dev.ecalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class EventAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: EventNotificationManager

    @Inject
    lateinit var alarmScheduler: EventAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EVENT_REMINDER -> {
                val uid = intent.getStringExtra(EXTRA_UID) ?: return
                val instanceStart = intent.getLongExtra(EXTRA_INSTANCE_START, 0L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Event"
                val location = intent.getStringExtra(EXTRA_LOCATION)
                val offsetMins = intent.getIntExtra(EXTRA_OFFSET_MINS, 15)

                notificationManager.showReminderNotification(
                    uid = uid,
                    instanceStart = instanceStart,
                    title = title,
                    location = location,
                    offsetMins = offsetMins,
                )
            }

            ACTION_SNOOZE -> {
                val uid = intent.getStringExtra(EXTRA_UID) ?: return
                val instanceStart = intent.getLongExtra(EXTRA_INSTANCE_START, 0L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Event"
                val location = intent.getStringExtra(EXTRA_LOCATION)

                // Dismiss current notification
                val notifId = "$uid$instanceStart".hashCode()
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .cancel(notifId)

                // Schedule snooze
                alarmScheduler.scheduleSnooze(uid, instanceStart, title, location)
                Timber.d("Snoozed reminder for $title")
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Timber.d("Boot completed — rescheduling all calendar alarms")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        alarmScheduler.rescheduleAll()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to reschedule alarms on boot")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_EVENT_REMINDER = "dev.ecalendar.ACTION_EVENT_REMINDER"
        const val ACTION_SNOOZE = "dev.ecalendar.ACTION_SNOOZE"
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_INSTANCE_START = "extra_instance_start"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_OFFSET_MINS = "extra_offset_mins"
    }
}
