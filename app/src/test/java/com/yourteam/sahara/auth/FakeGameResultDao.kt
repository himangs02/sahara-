package com.yourteam.sahara.auth

import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory GameResultDao that stores every patient's rows, so isolation must come from the repository. */
class FakeGameResultDao : GameResultDao {
    private val rows = MutableStateFlow<List<GameResultEntity>>(emptyList())
    private var nextId = 1

    override fun insertGameResult(gameResult: GameResultEntity): Long {
        val id = nextId++
        rows.value = rows.value + gameResult.copy(id = id)
        return id.toLong()
    }
    override fun getAllGameResults(): Flow<List<GameResultEntity>> =
        rows.map { list -> list.sortedByDescending { it.timestamp } }
    override fun getGameResultsForPatient(patientId: String): Flow<List<GameResultEntity>> =
        rows.map { list -> list.filter { it.patientId == patientId }.sortedByDescending { it.timestamp } }
    override fun getAllGameResultsSync(): List<GameResultEntity> = rows.value
    override fun getRecentGameResults(limit: Int): Flow<List<GameResultEntity>> =
        rows.map { list -> list.sortedByDescending { it.timestamp }.take(limit) }
    override fun getResultsForGameType(gameType: String): Flow<List<GameResultEntity>> =
        rows.map { list -> list.filter { it.gameType == gameType } }
}
