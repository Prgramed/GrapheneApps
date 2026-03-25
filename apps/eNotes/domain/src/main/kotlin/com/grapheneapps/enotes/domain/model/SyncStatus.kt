package com.grapheneapps.enotes.domain.model

enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DELETE,
    CONFLICT,
    ERROR,
    LOCAL_ONLY,
}
