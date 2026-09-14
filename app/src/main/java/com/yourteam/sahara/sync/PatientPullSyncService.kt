package com.yourteam.sahara.sync

import com.yourteam.sahara.api.BackendError
import com.yourteam.sahara.api.toBackendError
import com.yourteam.sahara.auth.CaregiverPatientEntity
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.data.remote.RemoteDataSource

/** Stage 3B, Part 5: after a caregiver's identity is verified against the real
 * backend (see [com.yourteam.sahara.data.remote.BackendAuthService]), pulls every
 * patient the backend says this caregiver is authorized for and links them to the
 * caregiver's *local* session in Room -- so they appear through the existing
 * [com.yourteam.sahara.auth.AuthRepository.patients] flow the UI already observes,
 * with no UI or ViewModel change needed. This is how a second device picks up a
 * patient created on a first device. */
class PatientPullSyncService(
    private val remoteDataSource: RemoteDataSource,
    private val database: AppDatabase
) {
    sealed class Outcome {
        data class Success(val patientCount: Int) : Outcome()
        data class Failed(val error: BackendError) : Outcome()
    }

    /** Never throws -- a caregiver with no network, or an unreachable backend, must
     * keep using whatever patients are already in local Room exactly as before. */
    suspend fun pullPatientsFor(localCaregiverId: String): Outcome = try {
        val patients = remoteDataSource.fetchPatients()
        patients.forEach { patient ->
            database.patientDao().insertPatient(patient.toEntity())
            database.authDao().link(
                CaregiverPatientEntity(
                    caregiverId = localCaregiverId,
                    patientId = patient.id,
                    consentAt = System.currentTimeMillis()
                )
            )
        }
        Outcome.Success(patients.size)
    } catch (t: Throwable) {
        Outcome.Failed(t.toBackendError())
    }
}
