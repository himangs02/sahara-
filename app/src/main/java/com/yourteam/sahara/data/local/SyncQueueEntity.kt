package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entityType: String, // "GAME_RESULT", "PATIENT", "REMINDER"
    val entityId: String,   // UUID syncId
    val operation: String = "INSERT",  // "INSERT", "UPDATE", "DELETE"
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long = 0L,
    val lastError: String? = null,
    val caregiverId: String? = null, // Owner caregiver; null for pre-migration items (treated as orphaned)
    // Only used for REMINDER items, which are patient-scoped API calls: needed to build the
    // /patients/{patientId}/reminders path, and required for DELETE since the Room row is
    // already gone by the time the queue is processed so it can't be looked up there.
    val patientId: String? = null
)
