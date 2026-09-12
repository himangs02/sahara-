package com.yourteam.sahara.data.repository

import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GameResultRepository(
    private val dao: GameResultDao,
    private val syncManager: SyncManager? = null
) {

    suspend fun saveGameResult(result: GameResult) = withContext(Dispatchers.IO) {
        dao.insertGameResult(result.toEntity())
        syncManager?.enqueueGameResultSync(result.syncId)
    }

    fun getAllGameResults(): Flow<List<GameResult>> {
        return dao.getAllGameResults().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getRecentGameResults(limit: Int): Flow<List<GameResult>> {
        return dao.getRecentGameResults(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getResultsForGameType(gameType: String): Flow<List<GameResult>> {
        return dao.getResultsForGameType(gameType).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
