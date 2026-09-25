package com.yourteam.sahara.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.sahara.auth.AuthDataSource
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.SyncQueueEntity
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.remote.RemoteDataSource
import com.yourteam.sahara.data.remote.SimulatedRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncManager(
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor,
    private val authDataSource: AuthDataSource? = null,
    val remoteDataSource: RemoteDataSource = SimulatedRemoteDataSource()
) {
    private val syncQueueDao = database.syncQueueDao()
    private val gameResultDao = database.gameResultDao()
    private val patientDao = database.patientDao()
    private val reminderDao = database.reminderDao()

    @Volatile
    var lastSuccessfulSyncTime: Long = System.currentTimeMillis()
        private set

    private val _isSyncing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingCountFlow: Flow<Int> = (authDataSource?.sessions ?: flowOf(null))
        .flatMapLatest { session ->
            val caregiverId = session?.caregiverId
            if (caregiverId != null) {
                syncQueueDao.getPendingCountFlowForCaregiver(caregiverId)
            } else {
                syncQueueDao.getPendingCountFlow()
            }
        }

    val syncStatusInfo: StateFlow<SyncStatusInfo> = combine(
        networkMonitor.isOnline,
        pendingCountFlow,
        _isSyncing
    ) { isOnline, pendingCount, isSyncing ->
        val state = when {
            isSyncing -> SyncState.SYNCING
            !isOnline && pendingCount > 0 -> SyncState.OFFLINE
            pendingCount > 0 -> SyncState.PENDING
            !isOnline -> SyncState.OFFLINE
            else -> SyncState.SYNCED
        }

        // SyncStatusCard turns the state into text in the current UI language.
        SyncStatusInfo(
            state = state,
            pendingCount = pendingCount,
            lastSuccessfulSyncTime = lastSuccessfulSyncTime
        )
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = SyncStatusInfo(SyncState.SYNCED)
    )

    /**
     * Enqueues [syncId] for upload, owned by whichever caregiver is authenticated in the local
     * session right now (never a value supplied by the caller). If no caregiver is authenticated,
     * the item is NOT queued for upload -- the local Room write already happened in the repository,
     * so the data is not lost, it simply will not sync until a caregiver session owns it.
     */
    suspend fun enqueueGameResultSync(syncId: String) = withContext(Dispatchers.IO) {
        val caregiverId = authDataSource?.session()?.caregiverId ?: return@withContext
        syncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "GAME_RESULT",
                entityId = syncId,
                operation = "INSERT",
                caregiverId = caregiverId
            )
        )
    }

    suspend fun enqueuePatientSync(syncId: String) = withContext(Dispatchers.IO) {
        val caregiverId = authDataSource?.session()?.caregiverId ?: return@withContext
        syncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "PATIENT",
                entityId = syncId,
                operation = "INSERT",
                caregiverId = caregiverId
            )
        )
    }

    /**
     * Enqueues a reminder create/update/delete for [reminderId] belonging to [patientId], owned
     * by whichever caregiver is authenticated right now (never caller-supplied). [patientId] is
     * captured here (not re-derived from Room later) because a DELETE's local row is already
     * gone by the time the queue is processed, and reminder endpoints are patient-scoped.
     *
     * Any other still-pending queue items for this same reminder are superseded and removed --
     * most importantly, queuing a DELETE drops an earlier queued INSERT/UPDATE for the same
     * reminder, so a delete can never be raced by a stale create on the *same* device (the
     * cross-device case is a documented limitation; see ReminderPullSyncService).
     */
    suspend fun enqueueReminderSync(reminderId: String, patientId: String, operation: String) = withContext(Dispatchers.IO) {
        val caregiverId = authDataSource?.session()?.caregiverId ?: return@withContext
        syncQueueDao.deletePendingItemsFor("REMINDER", reminderId)
        syncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "REMINDER",
                entityId = reminderId,
                operation = operation,
                caregiverId = caregiverId,
                patientId = patientId
            )
        )
    }

    suspend fun processPendingSyncQueue(): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext true
        _isSyncing.value = true

        try {
            val currentSession = authDataSource?.session()
            val currentCaregiverId = currentSession?.caregiverId

            val pendingItems = syncQueueDao.getPendingSyncItems()
            if (pendingItems.isEmpty()) {
                _isSyncing.value = false
                return@withContext true
            }

            var allSuccess = true

            for (item in pendingItems) {
                // Only process items that belong to the currently authenticated caregiver.
                // Skip items without an owner (orphaned from pre-migration data or demo) to prevent
                // cross-caregiver contamination.
                if (item.caregiverId == null) {
                    // Pre-migration or orphaned item; skip but don't fail the sync.
                    // Leave it pending for manual recovery if needed.
                    continue
                }
                if (item.caregiverId != currentCaregiverId) {
                    // Item belongs to another caregiver; skip.
                    continue
                }

                val success = when (item.entityType) {
                    "GAME_RESULT" -> {
                        val resultEntities = gameResultDao.getAllGameResultsSync()
                        val targetEntity = resultEntities.find { it.syncId == item.entityId }
                        if (targetEntity != null) {
                            remoteDataSource.uploadGameResult(targetEntity.toDomain())
                        } else {
                            true // Already removed or missing
                        }
                    }
                    "PATIENT" -> {
                        val patientEntity = patientDao.getPatientByIdSync(item.entityId)
                        if (patientEntity != null) {
                            remoteDataSource.uploadPatient(patientEntity.toDomain())
                        } else {
                            true
                        }
                    }
                    "REMINDER" -> {
                        val patientId = item.patientId
                        if (patientId == null) {
                            true // Malformed item (should never happen); drop rather than retry forever.
                        } else if (item.operation == "DELETE") {
                            remoteDataSource.deleteReminder(patientId, item.entityId)
                        } else {
                            val reminderEntity = reminderDao.getReminderById(item.entityId)
                            if (reminderEntity != null) {
                                remoteDataSource.uploadReminder(patientId, reminderEntity.toDomain())
                            } else {
                                true // Already deleted locally before this create/update synced.
                            }
                        }
                    }
                    else -> true
                }

                if (success) {
                    syncQueueDao.updateSyncItem(
                        item.copy(
                            syncStatus = "SYNCED",
                            lastAttemptAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    allSuccess = false
                    syncQueueDao.updateSyncItem(
                        item.copy(
                            syncStatus = "PENDING",
                            retryCount = item.retryCount + 1,
                            lastAttemptAt = System.currentTimeMillis(),
                            lastError = "Network error"
                        )
                    )
                }
            }

            if (allSuccess) {
                lastSuccessfulSyncTime = System.currentTimeMillis()
                syncQueueDao.deleteSyncedItems()
            }

            _isSyncing.value = false
            return@withContext allSuccess
        } catch (e: Exception) {
            _isSyncing.value = false
            return@withContext false
        }
    }

    fun triggerManualSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SaharaManualSync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
