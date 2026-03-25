package com.grapheneapps.enotes.data.preferences

data class AppPreferences(
    val sortOrder: Int = 0, // 0=editedAt DESC, 1=createdAt DESC, 2=title A-Z
    val themeMode: Int = 0, // 0=system, 1=light, 2=dark
    val syncIntervalMinutes: Int = 0, // 0=manual only
    val autoLockMinutes: Int = 0, // 0=immediate
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val lastSyncTime: Long = 0,
)
