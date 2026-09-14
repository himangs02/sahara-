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
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.data.remote.RemoteDataSource
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.reminder.FakeReminderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLinkedPatientsAuthDao(private val linked: Map<String, List<PatientEntity>>) : AuthDao {
    override suspend fun account(login: String): AccountEntity? = null
    override suspend fun accountById(id: String): AccountEntity? = null
    override suspend fun insertAccount(account: AccountEntity) {}
    override suspend fun link(link: CaregiverPatientEntity) {}
    override fun observeSession(): Flow<LocalSessionEntity?> = flowOf(null)
    override suspend fun session(): LocalSessionEntity? = null
    override suspend fun saveSession(session: LocalSessionEntity) {}
    override suspend fun clearSession() {}
    override suspend fun isLinked(caregiverId: String, patientId: String): Boolean =
        linked[caregiverId]?.any { it.id == patientId } ?: false
    override fun patients(caregiverId: String): Flow<List<PatientEntity>> = flowOf(linked[caregiverId].orEmpty())
}

private class FakeRemindersRemoteDataSource(
    private val remindersByPatient: Map<String, List<Reminder>> = emptyMap(),
    private val shouldFail: Boolean = false
) : RemoteDataSource {
    override suspend fun uploadGameResult(result: GameResult): Boolean = true
    override suspend fun uploadPatient(patient: Patient): Boolean = true
    override suspend fun fetchGameResults(): List<GameResult> = emptyList()
    override suspend fun fetchPatients(): List<Patient> = emptyList()
    override suspend fun fetchGameResultsForPatient(patientId: String): List<GameResult> = emptyList()
    override suspend fun uploadReminder(patientId: String, reminder: Reminder): Boolean = true
    override suspend fun deleteReminder(patientId: String, reminderId: String): Boolean = true
    override suspend fun fetchRemindersForPatient(patientId: String): List<Reminder> {
        if (shouldFail) throw java.net.UnknownHostException()
        return remindersByPatient[patientId].orEmpty()
    }
}

private class FakeReminderPullAppDatabase(private val authDao: AuthDao, private val reminderDao: ReminderDao) :
    com.yourteam.sahara.data.local.AppDatabase() {
    override fun authDao(): AuthDao = authDao
    override fun gameResultDao(): GameResultDao = throw UnsupportedOperationException()
    override fun patientDao(): PatientDao = throw UnsupportedOperationException()
    override fun syncQueueDao(): SyncQueueDao = throw UnsupportedOperationException()
    override fun reminderDao(): ReminderDao = reminderDao
    override fun caregiverAlertDao(): CaregiverAlertDao = throw UnsupportedOperationException()
    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
        androidx.room.InvalidationTracker(this, mapOf(), mapOf(), "reminders")
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper =
        throw UnsupportedOperationException()
}

class ReminderPullSyncServiceTest {

    private val patientA = PatientEntity(id = "p1", syncId = "p1", name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese")

    @Test fun `pulls reminders only for patients linked to this caregiver`() = runTest {
        val reminder = Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine", minuteOfDay = 480)
        val authDao = FakeLinkedPatientsAuthDao(mapOf("caregiver-1" to listOf(patientA)))
        val reminderDao = FakeReminderDao()
        val database = FakeReminderPullAppDatabase(authDao, reminderDao)
        val service = ReminderPullSyncService(FakeRemindersRemoteDataSource(mapOf("p1" to listOf(reminder))), database)

        val outcome = service.pullRemindersFor("caregiver-1")

        assertTrue(outcome is ReminderPullSyncService.Outcome.Success)
        assertEquals(1, (outcome as ReminderPullSyncService.Outcome.Success).reminderCount)
        assertEquals("rem1", reminderDao.getReminderById("rem1")?.id)
    }

    @Test fun `never pulls reminders for a patient not linked to this caregiver`() = runTest {
        // Caregiver B has no linked patients, so even if the fake remote had reminders for
        // patient p1 (belonging to caregiver A), pullRemindersFor("caregiver-B") must not fetch
        // or store them -- it only ever asks for patients this caregiver is actually linked to.
        val reminder = Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine", minuteOfDay = 480)
        val authDao = FakeLinkedPatientsAuthDao(mapOf("caregiver-A" to listOf(patientA), "caregiver-B" to emptyList()))
        val reminderDao = FakeReminderDao()
        val database = FakeReminderPullAppDatabase(authDao, reminderDao)
        val service = ReminderPullSyncService(FakeRemindersRemoteDataSource(mapOf("p1" to listOf(reminder))), database)

        val outcome = service.pullRemindersFor("caregiver-B")

        assertTrue(outcome is ReminderPullSyncService.Outcome.Success)
        assertEquals(0, (outcome as ReminderPullSyncService.Outcome.Success).reminderCount)
        assertEquals(null, reminderDao.getReminderById("rem1"))
    }

    @Test fun `pulling a reminder preserves this device's own completion state`() = runTest {
        val authDao = FakeLinkedPatientsAuthDao(mapOf("caregiver-1" to listOf(patientA)))
        val reminderDao = FakeReminderDao()
        // Locally, this device already marked the reminder completed today.
        reminderDao.insertReminder(
            Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine", minuteOfDay = 480, lastCompletedEpochDay = 20_709).toEntity()
        )
        val database = FakeReminderPullAppDatabase(authDao, reminderDao)
        // The server's definition (e.g. edited on another device) knows nothing of that completion.
        val serverReminder = Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine (edited)", minuteOfDay = 540)
        val service = ReminderPullSyncService(FakeRemindersRemoteDataSource(mapOf("p1" to listOf(serverReminder))), database)

        service.pullRemindersFor("caregiver-1")

        val merged = reminderDao.getReminderById("rem1")!!
        assertEquals("Morning Medicine (edited)", merged.title)
        assertEquals(540, merged.minuteOfDay)
        assertEquals(20_709L, merged.lastCompletedEpochDay) // preserved, not overwritten by the pull
    }

    @Test fun `a backend failure is caught and mapped, never thrown`() = runTest {
        val authDao = FakeLinkedPatientsAuthDao(mapOf("caregiver-1" to listOf(patientA)))
        val reminderDao = FakeReminderDao()
        val database = FakeReminderPullAppDatabase(authDao, reminderDao)
        val service = ReminderPullSyncService(FakeRemindersRemoteDataSource(shouldFail = true), database)

        val outcome = service.pullRemindersFor("caregiver-1")

        assertTrue(outcome is ReminderPullSyncService.Outcome.Failed)
        assertEquals(BackendError.NetworkUnavailable, (outcome as ReminderPullSyncService.Outcome.Failed).error)
    }
}
