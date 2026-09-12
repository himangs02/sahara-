package com.yourteam.sahara.sync

enum class SyncState {
    SYNCED,
    PENDING,
    SYNCING,
    FAILED,
    OFFLINE
}

data class SyncStatusInfo(
    val state: SyncState,
    val pendingCount: Int = 0,
    val lastSuccessfulSyncTime: Long = 0L,
    val statusMessage: String = ""
)
