package com.prgramed.eprayer.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.prgramed.eprayer.domain.model.AdhanSound
import com.prgramed.eprayer.domain.model.Prayer
import com.prgramed.eprayer.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var cachedAdhanSound: AdhanSound = AdhanSound.MOHAMMED_REFAAT

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            userPreferencesRepository.getUserPreferences()
                .catch { }
                .collect { prefs ->
                    val newSound = prefs.adhanSound
                    if (newSound != cachedAdhanSound) {
                        cachedAdhanSound = newSound
                        // Recreate channel with new sound
                        recreateNotificationChannel(newSound)
                    }
                }
        }
    }

    fun createNotificationChannel() {
        recreateNotificationChannel(cachedAdhanSound)
    }

    private fun recreateNotificationChannel(adhanSound: AdhanSound) {
        // Delete existing channel so we can change the sound
        notificationManager.deleteNotificationChannel(CHANNEL_ID)

        val soundUri = getAdhanSoundUri(adhanSound)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer Times",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Adhan notifications for prayer times"
            if (adhanSound == AdhanSound.SILENT) {
                setSound(null, null)
            } else if (soundUri != null) {
                setSound(soundUri, audioAttributes)
            }
            // else DEVICE_DEFAULT: leave as system default
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showPrayerNotification(prayer: Prayer) {
        val displayName = prayer.name.lowercase().replaceFirstChar { it.uppercase() }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(displayName)
            .setContentText("It's time for $displayName prayer")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(prayer.ordinal, builder.build())
    }

    private fun getAdhanSoundUri(sound: AdhanSound): Uri? = when (sound) {
        AdhanSound.MOHAMMED_REFAAT ->
            Uri.parse("android.resource://${context.packageName}/raw/adhan_refaat")
        AdhanSound.ABDEL_BASSET ->
            Uri.parse("android.resource://${context.packageName}/raw/adhan_abdel_basset")
        AdhanSound.AL_HUSARY ->
            Uri.parse("android.resource://${context.packageName}/raw/adhan_husary")
        AdhanSound.DEVICE_DEFAULT -> null
        AdhanSound.SILENT -> null
    }

    companion object {
        const val CHANNEL_ID = "eprayer_notifications"
    }
}
