package com.yourteam.sahara.data.remote

import com.yourteam.sahara.api.SaharaApiService
import com.yourteam.sahara.api.toCreateDto
import com.yourteam.sahara.api.toDomain
import com.yourteam.sahara.api.toUpsertDto
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder

/** The real, FastAPI-backed [RemoteDataSource] (Stage 3B). The JWT is attached
 * automatically by the OkHttp interceptor configured in [com.yourteam.sahara.api.RetrofitProvider]
 * -- this class never sees or handles the token directly. Every method here can throw
 * (network failure, HTTP error); [uploadGameResult]/[uploadPatient] swallow the
 * exception and return false to match [SimulatedRemoteDataSource]'s existing
 * Boolean-result contract that [com.yourteam.sahara.sync.SyncManager] already retries
 * on. The fetch methods intentionally let exceptions propagate -- their callers
 * (PatientPullSyncService) are expected to catch and map them, the same pattern
 * [com.yourteam.sahara.data.remote.BackendAuthService] already established. */
class RetrofitRemoteDataSource(private val api: SaharaApiService) : RemoteDataSource {

    override suspend fun uploadGameResult(result: GameResult): Boolean = runCatching {
        api.uploadGameResult(result.patientId, result.toUpsertDto())
    }.isSuccess

    override suspend fun uploadPatient(patient: Patient): Boolean = runCatching {
        api.createPatient(patient.toCreateDto())
    }.isSuccess

    /** Not used by the Stage 3B sync path (which is always patient-scoped); kept only
     * to satisfy the shared [RemoteDataSource] contract. */
    override suspend fun fetchGameResults(): List<GameResult> = emptyList()

    override suspend fun fetchPatients(): List<Patient> = api.getPatients().map { it.toDomain() }

    override suspend fun fetchGameResultsForPatient(patientId: String): List<GameResult> =
        api.getGameResults(patientId).map { it.toDomain() }

    override suspend fun uploadReminder(patientId: String, reminder: Reminder): Boolean = runCatching {
        api.uploadReminder(patientId, reminder.toUpsertDto())
    }.isSuccess

    override suspend fun deleteReminder(patientId: String, reminderId: String): Boolean = runCatching {
        api.deleteReminder(patientId, reminderId)
    }.fold(
        onSuccess = { true },
        // A 404 (already deleted, e.g. a retried delete) is still a successful outcome
        // for the queue; any other failure (network, 401, etc.) should be retried.
        onFailure = { it is retrofit2.HttpException && it.code() == 404 }
    )

    override suspend fun fetchRemindersForPatient(patientId: String): List<Reminder> =
        api.getReminders(patientId).map { it.toDomain() }
}
