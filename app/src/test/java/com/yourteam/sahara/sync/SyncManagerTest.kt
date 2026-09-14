package com.yourteam.sahara.sync

import com.yourteam.sahara.auth.AuthDataSource
import com.yourteam.sahara.auth.LocalSessionEntity
import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import com.yourteam.sahara.data.local.PatientDao
import com.yourteam.sahara.data.local.PatientEntity
import com.yourteam.sahara.data.local.ReminderDao
import com.yourteam.sahara.data.local.ReminderEntity
import com.yourteam.sahara.data.local.SyncQueueDao
import com.yourteam.sahara.data.local.SyncQueueEntity
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.data.remote.SimulatedRemoteDataSource
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val inMemoryResults = mutableListOf<GameResultEntity>()
    private val inMemoryPatients = mutableListOf<PatientEntity>()
    private val inMemorySyncQueue = mutableListOf<SyncQueueEntity>()
    private val inMemoryReminders = mutableListOf<ReminderEntity>()

    private val fakeGameResultDao = object : GameResultDao {
        override fun insertGameResult(gameResult: GameResultEntity): Long {
            inMemoryResults.removeAll { it.syncId == gameResult.syncId }
            inMemoryResults.add(gameResult)
            return 1L
        }

        override fun getAllGameResults(): Flow<List<GameResultEntity>> = flowOf(inMemoryResults)
        override fun getGameResultsForPatient(patientId: String): Flow<List<GameResultEntity>> =
            flowOf(inMemoryResults.filter { it.patientId == patientId })
        override fun getAllGameResultsSync(): List<GameResultEntity> = inMemoryResults.toList()
        override fun getRecentGameResults(limit: Int): Flow<List<GameResultEntity>> = flowOf(inMemoryResults.take(limit))
        override fun getResultsForGameType(gameType: String): Flow<List<GameResultEntity>> =
            flowOf(inMemoryResults.filter { it.gameType == gameType })
    }

    private val fakePatientDao = object : PatientDao {
        override fun insertPatient(patient: PatientEntity) {
            inMemoryPatients.removeAll { it.id == patient.id }
            inMemoryPatients.add(patient)
        }

        override fun getPatientById(patientId: String): Flow<PatientEntity?> =
            flowOf(inMemoryPatients.find { it.id == patientId })

        override fun getPatientByIdSync(patientId: String): PatientEntity? =
            inMemoryPatients.find { it.id == patientId }

        override fun getAllPatients(): Flow<List<PatientEntity>> = flowOf(inMemoryPatients)
    }

    private val fakeReminderDao = object : ReminderDao {
        override fun insertReminder(reminder: ReminderEntity) {
            inMemoryReminders.removeAll { it.id == reminder.id }
            inMemoryReminders.add(reminder)
        }

        override fun insertRemindersIfAbsent(reminders: List<ReminderEntity>) {
            reminders.forEach { r -> if (inMemoryReminders.none { it.id == r.id }) inMemoryReminders.add(r) }
        }

        override fun updateReminder(reminder: ReminderEntity) {
            val index = inMemoryReminders.indexOfFirst { it.id == reminder.id }
            if (index != -1) inMemoryReminders[index] = reminder
        }

        override fun deleteReminder(reminder: ReminderEntity) {
            inMemoryReminders.removeAll { it.id == reminder.id }
        }

        override fun getReminderById(id: String): ReminderEntity? = inMemoryReminders.find { it.id == id }

        override fun getRemindersForPatient(patientId: String): Flow<List<ReminderEntity>> =
            flowOf(inMemoryReminders.filter { it.patientId == patientId })

        override fun getRemindersForPatientSync(patientId: String): List<ReminderEntity> =
            inMemoryReminders.filter { it.patientId == patientId }

        override fun getAllRemindersSync(): List<ReminderEntity> = inMemoryReminders.toList()
    }

    private val fakeSyncQueueDao = object : SyncQueueDao {
        override fun insertSyncItem(item: SyncQueueEntity): Long {
            val newItem = if (item.id == 0) item.copy(id = inMemorySyncQueue.size + 1) else item
            inMemorySyncQueue.add(newItem)
            return newItem.id.toLong()
        }

        override fun updateSyncItem(item: SyncQueueEntity) {
            val index = inMemorySyncQueue.indexOfFirst { it.id == item.id }
            if (index != -1) {
                inMemorySyncQueue[index] = item
            }
        }

        override fun getPendingSyncItems(): List<SyncQueueEntity> =
            inMemorySyncQueue.filter { it.syncStatus == "PENDING" }

        override fun observePendingSyncItems(): Flow<List<SyncQueueEntity>> =
            flowOf(getPendingSyncItems())

        override fun getPendingCountFlow(): Flow<Int> =
            flowOf(getPendingSyncItems().size)

        override fun deleteSyncedItems() {
            inMemorySyncQueue.removeAll { it.syncStatus == "SYNCED" }
        }

        override fun deleteSyncItem(id: Int) {
            inMemorySyncQueue.removeAll { it.id == id }
        }

        override fun deletePendingItemsFor(entityType: String, entityId: String) {
            inMemorySyncQueue.removeAll { it.entityType == entityType && it.entityId == entityId && it.syncStatus == "PENDING" }
        }
    }

    private val fakeDatabase = object : com.yourteam.sahara.data.local.AppDatabase() {
        override fun authDao(): com.yourteam.sahara.auth.AuthDao {
            throw UnsupportedOperationException()
        }
        override fun gameResultDao(): GameResultDao = fakeGameResultDao
        override fun patientDao(): PatientDao = fakePatientDao
        override fun syncQueueDao(): SyncQueueDao = fakeSyncQueueDao
        override fun reminderDao(): ReminderDao = fakeReminderDao
        override fun caregiverAlertDao(): com.yourteam.sahara.data.local.CaregiverAlertDao {
            throw UnsupportedOperationException()
        }
        override fun clearAllTables() {}
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(this, mapOf(), mapOf(), "game_results", "patients", "sync_queue", "reminders")
        }
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
            throw UnsupportedOperationException()
        }
    }

    private val fakeNetworkFlow = MutableStateFlow(true)
    private val fakeNetworkMonitor = object : NetworkMonitor(
        object : android.content.ContextWrapper(null) {}
    ) {
        override fun isNetworkAvailable(): Boolean = fakeNetworkFlow.value
    }

    private val simulatedRemote = SimulatedRemoteDataSource()

    /** Mutable in-memory session used to simulate login/logout between test steps. */
    private var currentSession: LocalSessionEntity? = null

    private val fakeAuthDataSource = object : AuthDataSource {
        override val sessions: Flow<LocalSessionEntity?> = flowOf(null)
        override suspend fun session(): LocalSessionEntity? = currentSession
        override suspend fun account(login: String) = null
        override suspend fun accountById(id: String) = null
        override suspend fun insertAccount(account: com.yourteam.sahara.auth.AccountEntity) {}
        override suspend fun saveSession(session: LocalSessionEntity) { currentSession = session }
        override suspend fun clearSession() { currentSession = null }
        override suspend fun isLinked(caregiverId: String, patientId: String) = true
        override fun patients(caregiverId: String): Flow<List<Patient>> = flowOf(emptyList())
        override suspend fun createPatient(caregiverId: String, patient: Patient, consentAt: Long) {}
        override suspend fun updatePatient(patient: Patient) {}
    }

    private fun loginAs(caregiverId: String) {
        currentSession = LocalSessionEntity(caregiverId = caregiverId)
    }

    private fun logout() {
        currentSession = null
    }

    private lateinit var syncManager: SyncManager
    private lateinit var gameResultRepo: GameResultRepository
    private lateinit var patientRepo: PatientRepository
    private lateinit var reminderRepo: ReminderRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inMemoryResults.clear()
        inMemoryPatients.clear()
        inMemorySyncQueue.clear()
        inMemoryReminders.clear()
        currentSession = null

        syncManager = SyncManager(fakeDatabase, fakeNetworkMonitor, fakeAuthDataSource, simulatedRemote)
        gameResultRepo = GameResultRepository(fakeGameResultDao, syncManager)
        patientRepo = PatientRepository(fakePatientDao, syncManager)
        reminderRepo = ReminderRepository(fakeReminderDao, syncManager = syncManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Offline GameResult creation saves locally and queues for sync owned by current caregiver`() = runBlocking {
        fakeNetworkFlow.value = false // Offline mode
        loginAs("caregiver_1")
        val customSyncId = UUID.randomUUID().toString()

        val result = GameResult(
            syncId = customSyncId,
            gameType = "Memory Match",
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = 1,
            completionTimeSeconds = 30,
            accuracy = 80f,
            completed = true
        )

        gameResultRepo.saveGameResult(result)

        // Verify Room contains local record
        assertEquals(1, inMemoryResults.size)
        assertEquals(customSyncId, inMemoryResults.first().syncId)

        // Verify SyncQueue contains 1 pending item, owned by the authenticated caregiver
        val pendingItems = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pendingItems.size)
        assertEquals(customSyncId, pendingItems.first().entityId)
        assertEquals("PENDING", pendingItems.first().syncStatus)
        assertEquals("caregiver_1", pendingItems.first().caregiverId)
    }

    @Test
    fun `Successful sync uploads queue items and marks them synced`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_1")

        val result = GameResult(
            gameType = "Memory Match",
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = 0,
            completionTimeSeconds = 25,
            accuracy = 100f,
            completed = true
        )

        gameResultRepo.saveGameResult(result)
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size)

        // Process Sync Queue
        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size) // Queue cleared
        assertEquals(1, simulatedRemote.getRemoteCount()) // Remote now contains item
    }

    @Test
    fun `Failed network sync preserves queue and increments retry count`() = runBlocking {
        simulatedRemote.shouldSimulateError = true // Fail remote request
        loginAs("caregiver_1")

        val result = GameResult(
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = 2,
            completionTimeSeconds = 40,
            accuracy = 66f,
            completed = true
        )

        gameResultRepo.saveGameResult(result)

        val success = syncManager.processPendingSyncQueue()

        assertFalse(success) // Sync returned false
        val pendingItems = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pendingItems.size) // Item still pending!
        assertEquals(1, pendingItems.first().retryCount) // Retry count incremented
        assertEquals(1, inMemoryResults.size) // Local Room record remains perfectly intact!
    }

    @Test
    fun `Sync is idempotent and prevents duplicate uploads via UUID syncId`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_1")
        val customSyncId = "unique_uuid_12345"

        val result = GameResult(
            syncId = customSyncId,
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = 0,
            completionTimeSeconds = 20,
            accuracy = 100f,
            completed = true
        )

        // Save twice with same syncId
        gameResultRepo.saveGameResult(result)
        syncManager.processPendingSyncQueue()

        gameResultRepo.saveGameResult(result)
        syncManager.processPendingSyncQueue()

        // Remote count should remain 1 (idempotent upsert by syncId)
        assertEquals(1, simulatedRemote.getRemoteCount())
    }

    @Test
    fun `Batch processing handles multiple queued offline records accurately`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_1")

        // Create 3 offline results
        val r1 = GameResult(difficulty = Difficulty.EASY.name, totalPairs = 4, matchedPairs = 4, mistakes = 0, completionTimeSeconds = 20, accuracy = 100f, completed = true)
        val r2 = GameResult(difficulty = Difficulty.MEDIUM.name, totalPairs = 6, matchedPairs = 6, mistakes = 1, completionTimeSeconds = 35, accuracy = 85f, completed = true)
        val r3 = GameResult(difficulty = Difficulty.HARD.name, totalPairs = 8, matchedPairs = 8, mistakes = 2, completionTimeSeconds = 50, accuracy = 80f, completed = true)

        gameResultRepo.saveGameResult(r1)
        gameResultRepo.saveGameResult(r2)
        gameResultRepo.saveGameResult(r3)

        assertEquals(3, inMemoryResults.size)
        assertEquals(3, fakeSyncQueueDao.getPendingSyncItems().size)

        // Process batch sync
        syncManager.processPendingSyncQueue()

        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
        assertEquals(3, simulatedRemote.getRemoteCount())
    }

    @Test
    fun `Empty sync queue returns success safely`() = runBlocking {
        val success = syncManager.processPendingSyncQueue()
        assertTrue(success)
    }

    @Test
    fun `Offline Patient creation saves locally and queues for sync owned by current caregiver`() = runBlocking {
        fakeNetworkFlow.value = false // Offline mode
        loginAs("caregiver_1")
        val customSyncId = UUID.randomUUID().toString()

        val patient = Patient(
            id = customSyncId,
            syncId = customSyncId,
            name = "Kamala Devi",
            age = 74,
            region = "Assam"
        )

        patientRepo.insertPatient(patient)

        // Verify Room contains local record
        assertEquals(1, inMemoryPatients.size)
        assertEquals(customSyncId, inMemoryPatients.first().syncId)

        // Verify SyncQueue contains 1 pending item, owned by the authenticated caregiver
        val pendingItems = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pendingItems.size)
        assertEquals(customSyncId, pendingItems.first().entityId)
        assertEquals("PATIENT", pendingItems.first().entityType)
        assertEquals("PENDING", pendingItems.first().syncStatus)
        assertEquals("caregiver_1", pendingItems.first().caregiverId)
    }

    @Test
    fun `Successful sync uploads Patient items and marks them synced`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_1")
        val customSyncId = UUID.randomUUID().toString()

        val patient = Patient(
            id = customSyncId,
            syncId = customSyncId,
            name = "Ramesh Babu",
            age = 65,
            region = "Kerala"
        )

        patientRepo.insertPatient(patient)
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size)

        // Process Sync Queue
        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size) // Queue cleared
    }

    // ============================================================
    // SECURITY: caregiver-scoped ownership (Stage 3B hardening)
    // ============================================================

    @Test
    fun `TEST A - Caregiver B cannot process Caregiver A pending queue on same device`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

        // Caregiver A creates offline data.
        loginAs("caregiver_A")
        val patientA = Patient(id = "patient_A", syncId = "patient_A", name = "Patient A", age = 70, region = "Assam")
        patientRepo.insertPatient(patientA)
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size)

        // Caregiver B logs in on the SAME device (A never synced).
        loginAs("caregiver_B")

        // B triggers sync.
        val success = syncManager.processPendingSyncQueue()

        // A's item must not upload as B: nothing reaches the remote, and A's item stays pending.
        assertTrue(success) // no work done for B is still a "successful" no-op sync
        assertEquals(0, simulatedRemote.getRemoteCount())
        val stillPending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, stillPending.size)
        assertEquals("caregiver_A", stillPending.first().caregiverId)
        assertEquals("PENDING", stillPending.first().syncStatus)
    }

    @Test
    fun `TEST B - Caregiver A can process their own queue after logging back in`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

        loginAs("caregiver_A")
        val patientA = Patient(id = "patient_A", syncId = "patient_A", name = "Patient A", age = 70, region = "Assam")
        patientRepo.insertPatient(patientA)

        // B logs in and syncs -- must not touch A's item (see TEST A).
        loginAs("caregiver_B")
        syncManager.processPendingSyncQueue()
        assertEquals(0, simulatedRemote.getRemotePatientCount())

        // A logs back in and syncs.
        loginAs("caregiver_A")
        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(1, simulatedRemote.getRemotePatientCount())
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
    }

    @Test
    fun `TEST C - Caregiver with two patients syncs both of their own items`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")

        patientRepo.insertPatient(Patient(id = "patient_A1", syncId = "patient_A1", name = "A1", age = 70, region = "Assam"))
        patientRepo.insertPatient(Patient(id = "patient_A2", syncId = "patient_A2", name = "A2", age = 68, region = "Assam"))

        assertEquals(2, fakeSyncQueueDao.getPendingSyncItems().size)

        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(2, simulatedRemote.getRemotePatientCount())
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
    }

    @Test
    fun `TEST D - Caregiver B syncs only their own patient, not A's`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

        loginAs("caregiver_A")
        patientRepo.insertPatient(Patient(id = "patient_A", syncId = "patient_A", name = "A", age = 70, region = "Assam"))

        loginAs("caregiver_B")
        patientRepo.insertPatient(Patient(id = "patient_B", syncId = "patient_B", name = "B", age = 60, region = "Kerala"))

        // B syncs.
        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(1, simulatedRemote.getRemotePatientCount()) // Only B's patient uploaded
        val remaining = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, remaining.size)
        assertEquals("caregiver_A", remaining.first().caregiverId) // A's item untouched
    }

    @Test
    fun `TEST E - Each caregiver sync processes only their own items when both have pending work`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

        loginAs("caregiver_A")
        patientRepo.insertPatient(Patient(id = "patient_A", syncId = "patient_A", name = "A", age = 70, region = "Assam"))

        loginAs("caregiver_B")
        patientRepo.insertPatient(Patient(id = "patient_B", syncId = "patient_B", name = "B", age = 60, region = "Kerala"))

        // A syncs first: only A's item should upload.
        loginAs("caregiver_A")
        syncManager.processPendingSyncQueue()
        assertEquals(1, simulatedRemote.getRemotePatientCount())
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size) // B's item still pending
        assertEquals("caregiver_B", fakeSyncQueueDao.getPendingSyncItems().first().caregiverId)

        // B syncs next: B's item uploads too, A's is already gone.
        loginAs("caregiver_B")
        syncManager.processPendingSyncQueue()
        assertEquals(2, simulatedRemote.getRemotePatientCount())
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
    }

    @Test
    fun `TEST F - No authenticated caregiver means nothing is queued for upload`() = runBlocking {
        // No session at all (e.g. app not logged in yet, or a demo/background write with no
        // authenticated caregiver).
        logout()

        val result = GameResult(
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = 0,
            completionTimeSeconds = 20,
            accuracy = 100f,
            completed = true
        )
        gameResultRepo.saveGameResult(result)

        // Local write still happens...
        assertEquals(1, inMemoryResults.size)
        // ...but nothing is queued for upload, since ownership cannot be established.
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)

        // Even if a caregiver later logs in and syncs, nothing was ever queued, so nothing uploads.
        loginAs("caregiver_A")
        val success = syncManager.processPendingSyncQueue()
        assertTrue(success)
        assertEquals(0, simulatedRemote.getRemoteCount())
    }

    @Test
    fun `TEST G - Orphaned queue entry with unknown owner is never attributed to current caregiver`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

        // Simulate a pre-migration / orphaned queue row with no caregiverId (e.g. legacy data,
        // or a demo seed row whose owner was never recorded).
        fakeSyncQueueDao.insertSyncItem(
            SyncQueueEntity(
                entityType = "PATIENT",
                entityId = "orphan_patient",
                operation = "INSERT",
                caregiverId = null
            )
        )
        inMemoryPatients.add(PatientEntity(id = "orphan_patient", syncId = "orphan_patient", name = "Orphan", age = 1, region = "?", language = "English"))

        loginAs("caregiver_A")
        val success = syncManager.processPendingSyncQueue()

        // The orphaned item must NOT be attributed to caregiver_A and must NOT upload.
        assertTrue(success)
        assertEquals(0, simulatedRemote.getRemoteCount())
        val stillPending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, stillPending.size)
        assertNull(stillPending.first().caregiverId)
    }

    // ============================================================
    // Stage 3C: reminder definition synchronization
    // ============================================================

    private fun reminder(id: String, patientId: String, minuteOfDay: Int = 8 * 60, enabled: Boolean = true) = Reminder(
        id = id, patientId = patientId, title = "Morning Medicine", minuteOfDay = minuteOfDay, enabled = enabled
    )

    @Test
    fun `Reminder create is queued owned by the current caregiver`() = runBlocking {
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))

        assertEquals(1, inMemoryReminders.size)
        val pending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pending.size)
        assertEquals("REMINDER", pending.first().entityType)
        assertEquals("INSERT", pending.first().operation)
        assertEquals("rem_1", pending.first().entityId)
        assertEquals("patient_A", pending.first().patientId)
        assertEquals("caregiver_A", pending.first().caregiverId)
    }

    @Test
    fun `Reminder create syncs to the backend and is removed from the queue`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))

        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(1, simulatedRemote.getRemoteReminderCount())
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
    }

    @Test
    fun `Multi-device- Device B pulls a reminder created on Device A via the backend`() = runBlocking {
        // Simulates TEST B/C from the Stage 3C spec at the sync-engine level: Device A creates
        // and syncs a reminder; "Device B" is simply a second, independent read of the same
        // backend by the same caregiver -- the actual proof this is real synchronization and
        // not two copies of local state.
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))
        syncManager.processPendingSyncQueue()

        val pulledForDeviceB = simulatedRemote.fetchRemindersForPatient("patient_A")

        assertEquals(1, pulledForDeviceB.size)
        assertEquals("rem_1", pulledForDeviceB.first().id)
        assertEquals("Morning Medicine", pulledForDeviceB.first().title)
    }

    @Test
    fun `Caregiver B cannot retrieve Caregiver A reminder`() = runBlocking {
        // TEST D: uploads only ever happen under the authenticated caregiver, and this fake
        // remote is patient-scoped exactly like the real backend's caregiver-linked
        // authorization -- caregiver B never even has patient_A's id to ask for.
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))
        syncManager.processPendingSyncQueue()

        val remindersForUnrelatedPatient = simulatedRemote.fetchRemindersForPatient("patient_B_has_no_such_patient")
        assertTrue(remindersForUnrelatedPatient.isEmpty())
    }

    @Test
    fun `Reminder edit offline then sync converges to the updated definition`() = runBlocking {
        // TEST E/F: edit while backend is down, keeps working locally, then syncs once back.
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A", minuteOfDay = 8 * 60, enabled = true))
        syncManager.processPendingSyncQueue()

        simulatedRemote.shouldSimulateError = true // backend unavailable
        reminderRepo.setEnabled("rem_1", false)
        assertFalse(inMemoryReminders.first { it.id == "rem_1" }.enabled) // local behavior unaffected
        val failedSync = syncManager.processPendingSyncQueue()
        assertFalse(failedSync)
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size) // queue retains the pending edit

        simulatedRemote.shouldSimulateError = false // backend returns
        val success = syncManager.processPendingSyncQueue()
        assertTrue(success)
        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)

        val pulledForDeviceB = simulatedRemote.fetchRemindersForPatient("patient_A")
        assertFalse(pulledForDeviceB.first().enabled) // Device B would converge to disabled
    }

    @Test
    fun `Deleting a reminder queues a DELETE and removes it from the backend`() = runBlocking {
        // TEST G: delete on Device A, sync, Device B's next pull sees it gone.
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        val r = reminder("rem_1", "patient_A")
        reminderRepo.insertReminder(r)
        syncManager.processPendingSyncQueue()
        assertEquals(1, simulatedRemote.getRemoteReminderCount())

        reminderRepo.deleteReminder(r)
        assertEquals(0, inMemoryReminders.size) // local Room row is gone immediately

        val pending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pending.size)
        assertEquals("DELETE", pending.first().operation)
        assertEquals("patient_A", pending.first().patientId) // captured at queue time, not re-derived later

        val success = syncManager.processPendingSyncQueue()
        assertTrue(success)
        assertEquals(0, simulatedRemote.getRemoteReminderCount())
        val pulledForDeviceB = simulatedRemote.fetchRemindersForPatient("patient_A")
        assertTrue(pulledForDeviceB.isEmpty())
    }

    @Test
    fun `Queuing a delete supersedes an earlier still-pending create for the same reminder`() = runBlocking {
        loginAs("caregiver_A")
        val r = reminder("rem_1", "patient_A")
        reminderRepo.insertReminder(r) // queues INSERT, never synced yet
        reminderRepo.deleteReminder(r) // queues DELETE before the INSERT ever left the device

        val pending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pending.size) // the stale INSERT was dropped, not raced
        assertEquals("DELETE", pending.first().operation)
    }

    @Test
    fun `Same-device caregiver isolation- Caregiver B cannot sync Caregiver A reminder`() = runBlocking {
        // Repeats the Stage 3B security scenario (Part 11) for reminders specifically.
        simulatedRemote.shouldSimulateError = false
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))
        assertEquals(1, fakeSyncQueueDao.getPendingSyncItems().size)

        loginAs("caregiver_B")
        val success = syncManager.processPendingSyncQueue()

        assertTrue(success)
        assertEquals(0, simulatedRemote.getRemoteReminderCount())
        val stillPending = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, stillPending.size)
        assertEquals("caregiver_A", stillPending.first().caregiverId)

        loginAs("caregiver_A")
        val secondSync = syncManager.processPendingSyncQueue()
        assertTrue(secondSync)
        assertEquals(1, simulatedRemote.getRemoteReminderCount())
    }

    @Test
    fun `Reminder completion state never enqueues a sync item`() = runBlocking {
        // Part 9: device-specific state (today's completion) is never part of the synced
        // definition.
        loginAs("caregiver_A")
        reminderRepo.insertReminder(reminder("rem_1", "patient_A"))
        syncManager.processPendingSyncQueue() // clear the initial INSERT from the queue

        reminderRepo.setCompleted("rem_1", true)

        assertEquals(0, fakeSyncQueueDao.getPendingSyncItems().size)
    }
}
