package com.grapheneapps.enotes.domain.model

data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val iconEmoji: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
