package dev.emusic.data.preferences

data class AppPreferences(
    val serverUrl: String = "",
    val username: String = "",
    val maxBitrate: Int = 0,
    val wifiOnlyDownloads: Boolean = true,
    val forceOfflineMode: Boolean = false,
    val scrobblingEnabled: Boolean = true,
    val headsUpNotificationsEnabled: Boolean = true,
    val replayGainMode: Int = 3, // OFF by default (index into ReplayGainMode)
    val preAmpDb: Float = 0f,
    val themeMode: Int = 0, // 0=system, 1=light, 2=dark
    val equalizerEnabled: Boolean = false,
    val eqBandLevels: String = "",
    val eqBassBoost: Int = 0,
    val eqVirtualizer: Int = 0,
    val eqActivePreset: String = "Flat",
    val crossfadeDurationMs: Int = 0,
    val gaplessPlayback: Boolean = true,
)
