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
    private val syncManager: SyncManager? = null
) {

    suspend fun insertPatient(patient: Patient) = withContext(Dispatchers.IO) {
        dao.insertPatient(patient.toEntity())
        syncManager?.enqueuePatientSync(patient.syncId)
    }

    fun getPatientById(patientId: String): Flow<Patient?> {
        return dao.getPatientById(patientId).map { entity ->
            entity?.toDomain()
        }
    }

    fun getAllPatients(): Flow<List<Patient>> {
        return dao.getAllPatients().map { entities ->
            if (entities.isEmpty()) {
                listOf(Patient())
            } else {
                entities.map { it.toDomain() }
            }
        }
    }
}
