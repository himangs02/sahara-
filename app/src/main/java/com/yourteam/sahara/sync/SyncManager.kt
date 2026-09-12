package com.yourteam.sahara.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.SyncQueueEntity
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.remote.RemoteDataSource
import com.yourteam.sahara.data.remote.SimulatedRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncManager(
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor,
    val remoteDataSource: RemoteDataSource = SimulatedRemoteDataSource()
) {
    private val syncQueueDao = database.syncQueueDao()
    private val gameResultDao = database.gameResultDao()
    private val patientDao = database.patientDao()

    @Volatile
    var lastSuccessfulSyncTime: Long = System.currentTimeMillis()
        private set

    private val _isSyncing = MutableStateFlow(false)

    val syncStatusInfo: StateFlow<SyncStatusInfo> = combine(
        networkMonitor.isOnline,
        syncQueueDao.getPendingCountFlow(),
        _isSyncing
    ) { isOnline, pendingCount, isSyncing ->
        val state = when {
            isSyncing -> SyncState.SYNCING
            !isOnline && pendingCount > 0 -> SyncState.OFFLINE
            pendingCount > 0 -> SyncState.PENDING
            !isOnline -> SyncState.OFFLINE
            else -> SyncState.SYNCED
        }

        val message = when (state) {
            SyncState.SYNCED -> "✓ All data synced"
            SyncState.PENDING -> "↻ $pendingCount activities waiting to sync"
            SyncState.SYNCING -> "↻ Syncing..."
            SyncState.OFFLINE -> if (pendingCount > 0) "↻ $pendingCount activities saved locally" else "Offline Mode"
            SyncState.FAILED -> "⚠ Sync failed — Will retry automatically"
        }

        SyncStatusInfo(
            state = state,
            pendingCount = pendingCount,
            lastSuccessfulSyncTime = lastSuccessfulSyncTime,
            statusMessage = message
        )
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = SyncStatusInfo(SyncState.SYNCED)
    )

    suspend fun enqueueGameResultSync(syncId: String) = withContext(Dispatchers.IO) {
        syncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "GAME_RESULT",
                entityId = syncId,
                operation = "INSERT"
            )
        )
    }

    suspend fun enqueuePatientSync(syncId: String) = withContext(Dispatchers.IO) {
        syncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "PATIENT",
                entityId = syncId,
                operation = "INSERT"
            )
        )
    }

    suspend fun processPendingSyncQueue(): Boolean = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext true
        _isSyncing.value = true

        try {
            val pendingItems = syncQueueDao.getPendingSyncItems()
            if (pendingItems.isEmpty()) {
                _isSyncing.value = false
                return@withContext true
            }

            var allSuccess = true

            for (item in pendingItems) {
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
