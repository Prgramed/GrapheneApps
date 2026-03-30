package dev.egallery.domain.model

data class SyncStats(
    val itemsSynced: Int = 0,
    val itemsDeleted: Int = 0,
    val albumsSynced: Int = 0,
    val peopleSynced: Int = 0,
    val tagsSynced: Int = 0,
    val durationMs: Long = 0,
)
