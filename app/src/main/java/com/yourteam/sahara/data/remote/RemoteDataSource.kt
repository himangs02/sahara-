package com.yourteam.sahara.data.remote

import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient

interface RemoteDataSource {
    suspend fun uploadGameResult(result: GameResult): Boolean
    suspend fun uploadPatient(patient: Patient): Boolean
    suspend fun fetchGameResults(): List<GameResult>
}
