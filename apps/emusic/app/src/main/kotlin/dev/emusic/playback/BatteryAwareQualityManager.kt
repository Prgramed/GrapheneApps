package dev.emusic.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.emusic.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryAwareQualityManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _maxBitRate = MutableStateFlow(0)
    val maxBitRate: StateFlow<Int> = _maxBitRate.asStateFlow()

    // Cached pref — updated via flow observer
    @Volatile private var userMaxBitrate: Int = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Use cached pref — no coroutine launch needed
            if (userMaxBitrate > 0) {
                _maxBitRate.value = userMaxBitrate
                return
            }

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100) / scale else 100

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            _maxBitRate.value = when {
                isCharging -> 0        // Original quality
                pct > 50 -> 320
                pct > 20 -> 192
                else -> 96
            }
        }
    }

    init {
        // Cache pref updates
        scope.launch {
            preferencesRepository.preferencesFlow
                .catch { }
                .collect { prefs ->
                    userMaxBitrate = prefs.maxBitrate
                }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }
}
