package com.yourteam.sahara.sync

import com.yourteam.sahara.api.BackendError
import com.yourteam.sahara.auth.AccountEntity
import com.yourteam.sahara.auth.AuthDao
import com.yourteam.sahara.auth.CaregiverPatientEntity
import com.yourteam.sahara.auth.LocalSessionEntity
import com.yourteam.sahara.data.local.CaregiverAlertDao
import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.PatientDao
import com.yourteam.sahara.data.local.PatientEntity
import com.yourteam.sahara.data.local.ReminderDao
import com.yourteam.sahara.data.local.SyncQueueDao
import com.yourteam.sahara.data.remote.RemoteDataSource
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePatientDao : PatientDao {
    val patients = mutableListOf<PatientEntity>()
    override fun insertPatient(patient: PatientEntity) {
        patients.removeAll { it.id == patient.id }
        patients.add(patient)
    }
    override fun getPatientById(patientId: String): Flow<PatientEntity?> = flowOf(patients.find { it.id == patientId })
    override fun getPatientByIdSync(patientId: String): PatientEntity? = patients.find { it.id == patientId }
    override fun getAllPatients(): Flow<List<PatientEntity>> = flowOf(patients)
}

private class FakeAuthDao : AuthDao {
    val links = mutableListOf<CaregiverPatientEntity>()
    override suspend fun account(login: String): AccountEntity? = null
    override suspend fun accountById(id: String): AccountEntity? = null
    override suspend fun insertAccount(account: AccountEntity) {}
    override suspend fun link(link: CaregiverPatientEntity) {
        if (links.none { it.caregiverId == link.caregiverId && it.patientId == link.patientId }) links.add(link)
    }
    override fun observeSession(): Flow<LocalSessionEntity?> = flowOf(null)
    override suspend fun session(): LocalSessionEntity? = null
    override suspend fun saveSession(session: LocalSessionEntity) {}
    override suspend fun clearSession() {}
    override suspend fun isLinked(caregiverId: String, patientId: String): Boolean =
        links.any { it.caregiverId == caregiverId && it.patientId == patientId }
    override fun patients(caregiverId: String): Flow<List<com.yourteam.sahara.data.local.PatientEntity>> = flowOf(emptyList())
}

private class FakeRemoteDataSource(
    private val patients: List<Patient> = emptyList(),
    private val shouldFail: Boolean = false,
    private val remindersByPatient: Map<String, List<Reminder>> = emptyMap()
) : RemoteDataSource {
    override suspend fun uploadGameResult(result: GameResult): Boolean = true
    override suspend fun uploadPatient(patient: Patient): Boolean = true
    override suspend fun fetchGameResults(): List<GameResult> = emptyList()
    override suspend fun fetchPatients(): List<Patient> {
        if (shouldFail) throw java.net.UnknownHostException()
        return patients
    }
    override suspend fun fetchGameResultsForPatient(patientId: String): List<GameResult> = emptyList()
    override suspend fun uploadReminder(patientId: String, reminder: Reminder): Boolean = true
    override suspend fun deleteReminder(patientId: String, reminderId: String): Boolean = true
    override suspend fun fetchRemindersForPatient(patientId: String): List<Reminder> {
        if (shouldFail) throw java.net.UnknownHostException()
        return remindersByPatient[patientId].orEmpty()
    }
}

private class FakeAppDatabase(
    private val patientDao: FakePatientDao,
    private val authDao: FakeAuthDao,
    private val reminderDao: ReminderDao = com.yourteam.sahara.reminder.FakeReminderDao()
) : com.yourteam.sahara.data.local.AppDatabase() {
    override fun authDao(): AuthDao = authDao
    override fun gameResultDao(): GameResultDao = throw UnsupportedOperationException()
    override fun patientDao(): PatientDao = patientDao
    override fun syncQueueDao(): SyncQueueDao = throw UnsupportedOperationException()
    override fun reminderDao(): ReminderDao = reminderDao
    override fun caregiverAlertDao(): CaregiverAlertDao = throw UnsupportedOperationException()
    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
        androidx.room.InvalidationTracker(this, mapOf(), mapOf(), "patients")
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper =
        throw UnsupportedOperationException()
}

class PatientPullSyncServiceTest {

    private val demoPatient = Patient(syncId = "p1", id = "p1", name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese")

    @Test fun `pulls patients into Room and links them to the local caregiver`() = runTest {
        val patientDao = FakePatientDao()
        val authDao = FakeAuthDao()
        val database = FakeAppDatabase(patientDao, authDao)
        val service = PatientPullSyncService(FakeRemoteDataSource(patients = listOf(demoPatient)), database)

        val outcome = service.pullPatientsFor("caregiver-1")

        assertTrue(outcome is PatientPullSyncService.Outcome.Success)
        assertEquals(1, (outcome as PatientPullSyncService.Outcome.Success).patientCount)
        assertEquals(1, patientDao.patients.size)
        assertEquals("p1", patientDao.patients.first().id)
        assertTrue(authDao.links.any { it.caregiverId == "caregiver-1" && it.patientId == "p1" })
    }

    @Test fun `a second pull is idempotent -- no duplicate patients or links`() = runTest {
        val patientDao = FakePatientDao()
        val authDao = FakeAuthDao()
        val database = FakeAppDatabase(patientDao, authDao)
        val service = PatientPullSyncService(FakeRemoteDataSource(patients = listOf(demoPatient)), database)

        service.pullPatientsFor("caregiver-1")
        service.pullPatientsFor("caregiver-1")

        assertEquals(1, patientDao.patients.size)
        assertEquals(1, authDao.links.count { it.caregiverId == "caregiver-1" && it.patientId == "p1" })
    }

    @Test fun `a backend failure is caught and mapped, never thrown`() = runTest {
        val patientDao = FakePatientDao()
        val authDao = FakeAuthDao()
        val database = FakeAppDatabase(patientDao, authDao)
        val service = PatientPullSyncService(FakeRemoteDataSource(shouldFail = true), database)

        val outcome = service.pullPatientsFor("caregiver-1")

        assertTrue(outcome is PatientPullSyncService.Outcome.Failed)
        assertEquals(BackendError.NetworkUnavailable, (outcome as PatientPullSyncService.Outcome.Failed).error)
        assertEquals(0, patientDao.patients.size)
    }
}
