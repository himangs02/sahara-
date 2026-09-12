package com.yourteam.sahara.sync

import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import com.yourteam.sahara.data.local.PatientDao
import com.yourteam.sahara.data.local.PatientEntity
import com.yourteam.sahara.data.local.SyncQueueDao
import com.yourteam.sahara.data.local.SyncQueueEntity
import com.yourteam.sahara.data.remote.SimulatedRemoteDataSource
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
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

    private val fakeGameResultDao = object : GameResultDao {
        override fun insertGameResult(gameResult: GameResultEntity): Long {
            inMemoryResults.removeAll { it.syncId == gameResult.syncId }
            inMemoryResults.add(gameResult)
            return 1L
        }

        override fun getAllGameResults(): Flow<List<GameResultEntity>> = flowOf(inMemoryResults)
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
    }

    private val fakeDatabase = object : com.yourteam.sahara.data.local.AppDatabase() {
        override fun gameResultDao(): GameResultDao = fakeGameResultDao
        override fun patientDao(): PatientDao = fakePatientDao
        override fun syncQueueDao(): SyncQueueDao = fakeSyncQueueDao
        override fun reminderDao(): com.yourteam.sahara.data.local.ReminderDao {
            throw UnsupportedOperationException()
        }
        override fun caregiverAlertDao(): com.yourteam.sahara.data.local.CaregiverAlertDao {
            throw UnsupportedOperationException()
        }
        override fun clearAllTables() {}
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(this, mapOf(), mapOf(), "game_results", "patients", "sync_queue")
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
    private lateinit var syncManager: SyncManager
    private lateinit var gameResultRepo: GameResultRepository
    private lateinit var patientRepo: PatientRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inMemoryResults.clear()
        inMemoryPatients.clear()
        inMemorySyncQueue.clear()

        syncManager = SyncManager(fakeDatabase, fakeNetworkMonitor, simulatedRemote)
        gameResultRepo = GameResultRepository(fakeGameResultDao, syncManager)
        patientRepo = PatientRepository(fakePatientDao, syncManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Offline GameResult creation saves locally and queues for sync`() = runBlocking {
        fakeNetworkFlow.value = false // Offline mode
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

        // Verify SyncQueue contains 1 pending item
        val pendingItems = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pendingItems.size)
        assertEquals(customSyncId, pendingItems.first().entityId)
        assertEquals("PENDING", pendingItems.first().syncStatus)
    }

    @Test
    fun `Successful sync uploads queue items and marks them synced`() = runBlocking {
        simulatedRemote.shouldSimulateError = false

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
    fun `Offline Patient creation saves locally and queues for sync`() = runBlocking {
        fakeNetworkFlow.value = false // Offline mode
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

        // Verify SyncQueue contains 1 pending item
        val pendingItems = fakeSyncQueueDao.getPendingSyncItems()
        assertEquals(1, pendingItems.size)
        assertEquals(customSyncId, pendingItems.first().entityId)
        assertEquals("PATIENT", pendingItems.first().entityType)
        assertEquals("PENDING", pendingItems.first().syncStatus)
    }

    @Test
    fun `Successful sync uploads Patient items and marks them synced`() = runBlocking {
        simulatedRemote.shouldSimulateError = false
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
}
