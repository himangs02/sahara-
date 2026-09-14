package com.yourteam.sahara.auth

import com.yourteam.sahara.model.Patient
import kotlinx.coroutines.flow.Flow

/** Local persistence boundary; remote authentication can later be composed behind AuthRepository. */
interface AuthDataSource {
    val sessions: Flow<LocalSessionEntity?>
    suspend fun session(): LocalSessionEntity?
    suspend fun account(login: String): AccountEntity?
    suspend fun accountById(id: String): AccountEntity?
    suspend fun insertAccount(account: AccountEntity)
    suspend fun saveSession(session: LocalSessionEntity)
    suspend fun clearSession()
    suspend fun isLinked(caregiverId: String, patientId: String): Boolean
    fun patients(caregiverId: String): Flow<List<Patient>>
    suspend fun createPatient(caregiverId: String, patient: Patient, consentAt: Long)
    suspend fun updatePatient(patient: Patient)
}
