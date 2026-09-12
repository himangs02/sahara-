package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entityType: String, // "GAME_RESULT", "PATIENT"
    val entityId: String,   // UUID syncId
    val operation: String = "INSERT",  // "INSERT", "UPDATE"
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0L,
    val lastError: String? = null
)
