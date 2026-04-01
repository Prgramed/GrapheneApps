package dev.equran

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.equran.data.repository.TopicSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EQuranApp : Application() {

    @Inject lateinit var topicSeeder: TopicSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        appScope.launch { topicSeeder.seedIfNeeded() }
    }
}
