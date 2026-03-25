package com.grapheneapps.enotes.domain.model

data class Note(
    val id: String,
    val title: String,
    val bodyJson: String = "",
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val encryptedBody: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val remoteEtag: String? = null,
)
