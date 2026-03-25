package com.prgramed.econtacts

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.prgramed.econtacts.domain.repository.CardDavRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ContactsApplication : Application() {

    @Inject
    lateinit var cardDavRepository: CardDavRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            cardDavRepository.ensurePeriodicSyncIfConfigured()
        }
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            "ongoing_call",
            "Ongoing calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shows notification during active phone calls"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
