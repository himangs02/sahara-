package com.yourteam.sahara.data.repository

import com.yourteam.sahara.data.local.PatientDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PatientRepository(
    private val dao: PatientDao,
    private val syncManager: SyncManager? = null,
    private val auth: com.yourteam.sahara.auth.AuthRepository? = null
) {

    suspend fun insertPatient(patient: Patient) = withContext(Dispatchers.IO) {
        auth?.requirePatient(patient.id)
        dao.insertPatient(patient.toEntity())
        syncManager?.enqueuePatientSync(patient.syncId)
    }

    fun getPatientById(patientId: String): Flow<Patient?> {
        val authRepo = auth
        if (authRepo != null) return kotlinx.coroutines.flow.combine(dao.getPatientById(patientId), authRepo.sessions) { entity, _ ->
            if (runCatching { authRepo.requirePatient(patientId) }.isSuccess) entity?.toDomain() else null
        }
        return dao.getPatientById(patientId).map { entity ->
            entity?.toDomain()
        }
    }

    fun getAllPatients(): Flow<List<Patient>> {
        if (auth != null) return auth.patients()
        return dao.getAllPatients().map { entities ->
            if (entities.isEmpty()) {
                emptyList()
            } else {
                entities.map { it.toDomain() }
            }
        }
    }
}
