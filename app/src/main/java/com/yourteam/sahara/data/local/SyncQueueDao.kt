package com.yourteam.sahara.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSyncItem(item: SyncQueueEntity): Long

    @Update
    fun updateSyncItem(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    fun observePendingSyncItems(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE syncStatus = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE syncStatus = 'PENDING' AND caregiverId = :caregiverId")
    fun getPendingCountFlowForCaregiver(caregiverId: String): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE syncStatus = 'SYNCED'")
    fun deleteSyncedItems()

    @Query("DELETE FROM sync_queue WHERE id = :id")
    fun deleteSyncItem(id: Int)

    /** Drops any still-pending queue items for one entity, so a new operation (most
     * importantly a DELETE) supersedes an earlier queued one instead of racing it. */
    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId AND syncStatus = 'PENDING'")
    fun deletePendingItemsFor(entityType: String, entityId: String)
}
