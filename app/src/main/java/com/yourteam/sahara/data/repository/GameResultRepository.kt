package com.yourteam.sahara.data.repository

import com.yourteam.sahara.auth.AuthRepository
import com.yourteam.sahara.auth.DemoIdentity
import com.yourteam.sahara.auth.PatientAccessDeniedException
import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Game history for one patient. Reads are filtered in SQL by [patientId]; when [auth] is supplied,
 * results are also withheld unless that patient is the caregiver's active, linked selection.
 */
class GameResultRepository(
    private val dao: GameResultDao,
    private val syncManager: SyncManager? = null,
    val patientId: String = DemoIdentity.PATIENT_ID,
    private val auth: AuthRepository? = null
) {

    suspend fun saveGameResult(result: GameResult) = withContext(Dispatchers.IO) {
        auth?.requirePatient(patientId)
        if (result.patientId != patientId) throw PatientAccessDeniedException("result belongs to another patient")
        dao.insertGameResult(result.toEntity())
        syncManager?.enqueueGameResultSync(result.syncId)
    }

    fun getAllGameResults(): Flow<List<GameResult>> {
        val rows = dao.getGameResultsForPatient(patientId).map { entities -> entities.map { it.toDomain() } }
        val authRepo = auth ?: return rows
        return combine(rows, authRepo.sessions) { values, session ->
            if (session?.selectedPatientId == patientId && runCatching { authRepo.requirePatient(patientId) }.isSuccess) values else emptyList()
        }
    }

    fun getRecentGameResults(limit: Int): Flow<List<GameResult>> = getAllGameResults().map { it.take(limit.coerceAtLeast(0)) }

    fun getResultsForGameType(gameType: String): Flow<List<GameResult>> = getAllGameResults().map { rows -> rows.filter { it.gameType == gameType } }
}
