package com.yourteam.sahara.data.remote

import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SimulatedRemoteDataSource : RemoteDataSource {

    private val remoteResults = ConcurrentHashMap<String, GameResult>()
    private val remotePatients = ConcurrentHashMap<String, Patient>()

    var shouldSimulateError = false

    override suspend fun uploadGameResult(result: GameResult): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(300) // Simulate network latency
        remoteResults[result.syncId] = result // Idempotent upsert by syncId
        true
    }

    override suspend fun uploadPatient(patient: Patient): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(200)
        remotePatients[patient.syncId] = patient
        true
    }

    override suspend fun fetchGameResults(): List<GameResult> = withContext(Dispatchers.IO) {
        delay(300)
        remoteResults.values.toList()
    }

    fun getRemoteCount(): Int = remoteResults.size
}
