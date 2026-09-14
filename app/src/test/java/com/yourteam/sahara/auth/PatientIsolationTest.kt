package com.yourteam.sahara.auth

import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.reminder.FakeReminderDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Proves a caregiver viewing Patient A can never read or write Patient B's data through the
 * patient-scoped repositories, and that logging out cuts off access entirely.
 */
class PatientIsolationTest {

    private val source = FakeAuthDataSource()
    private val auth = AuthRepository(source)
    private val gameDao = FakeGameResultDao()
    private val reminderDao = FakeReminderDao()

    private lateinit var patientA: String
    private lateinit var patientB: String

    private fun gameResult(patientId: String, accuracy: Float) = GameResult(
        patientId = patientId, gameType = "MEMORY_MATCH", difficulty = Difficulty.EASY.name,
        totalPairs = 4, matchedPairs = 4, mistakes = 1, completionTimeSeconds = 30,
        accuracy = accuracy, completed = true
    )

    // A-scoped repositories, exactly as SaharaApplication builds them for the selected patient.
    private fun gamesForA() = GameResultRepository(gameDao, syncManager = null, patientId = patientA, auth = auth)
    private fun remindersForA() = ReminderRepository(reminderDao, Dispatchers.Unconfined, scopePatientId = patientA, auth = auth)

    private inline fun expectDenied(block: () -> Unit) {
        try {
            block(); fail("Expected access to be denied")
        } catch (_: PatientAccessDeniedException) {
        }
    }

    @Before fun setUp() = runBlocking {
        auth.register("caregiver@home", "correct horse battery", consent = true)
        patientA = auth.savePatient(null, "Patient A", 70, "Assam", "Assamese", consent = true).id
        patientB = auth.savePatient(null, "Patient B", 72, "Assam", "Hindi", consent = true).id

        // Seed both patients' data straight into the shared DAOs, bypassing access checks.
        gameDao.insertGameResult(gameResult(patientA, 90f).toEntity())
        gameDao.insertGameResult(gameResult(patientB, 40f).toEntity())
        reminderDao.insertReminder(Reminder(id = "a-med", patientId = patientA, title = "A medicine", minuteOfDay = 8 * 60).toEntity())
        reminderDao.insertReminder(Reminder(id = "b-med", patientId = patientB, title = "B medicine", minuteOfDay = 9 * 60).toEntity())
    }

    @Test fun `A-scoped repositories return only Patient A data when A is selected`() = runBlocking {
        auth.selectPatient(patientA)

        val games = gamesForA().getAllGameResults().first()
        assertEquals(listOf(patientA), games.map { it.patientId })
        assertEquals(90f, games.single().accuracy, 0.01f)

        val reminders = remindersForA().getRemindersForPatient().first()
        assertEquals(listOf("a-med"), reminders.map { it.id })
    }

    @Test fun `A-scoped repositories reveal nothing while Patient B is selected`() = runBlocking {
        auth.selectPatient(patientB)

        assertTrue("Game history must not leak across the active selection", gamesForA().getAllGameResults().first().isEmpty())
        assertTrue("Reminders must not leak across the active selection", remindersForA().getRemindersForPatient().first().isEmpty())
    }

    @Test fun `Patient A cannot read a specific Patient B record`() = runBlocking {
        auth.selectPatient(patientA)
        // Directly requesting B's reminder id through the A-scoped repository is denied.
        expectDenied { runBlocking { remindersForA().getReminder("b-med") } }
        // Querying B's id through an A-scoped repository is denied.
        expectDenied { remindersForA().getRemindersForPatient(patientB) }
    }

    @Test fun `Patient A repository cannot write data belonging to Patient B`() = runBlocking {
        auth.selectPatient(patientA)
        expectDenied { runBlocking { gamesForA().saveGameResult(gameResult(patientB, 100f)) } }
        expectDenied { runBlocking { remindersForA().insertReminder(Reminder(patientId = patientB, title = "sneaky", minuteOfDay = 60)) } }

        // Patient A's own data still saves and stays scoped to A.
        gamesForA().saveGameResult(gameResult(patientA, 55f))
        assertEquals(2, gamesForA().getAllGameResults().first().size)
    }

    @Test fun `saving through the A-scoped repository requires A to be the active patient`() {
        runBlocking { auth.selectPatient(patientB) }
        // Even A's own record cannot be written while B is the selected patient.
        expectDenied { runBlocking { gamesForA().saveGameResult(gameResult(patientA, 80f)) } }
    }

    @Test fun `logging out blocks all patient data access`() = runBlocking {
        auth.selectPatient(patientA)
        auth.logout()

        assertTrue(gamesForA().getAllGameResults().first().isEmpty())
        assertTrue(remindersForA().getRemindersForPatient().first().isEmpty())
        expectDenied { runBlocking { gamesForA().saveGameResult(gameResult(patientA, 80f)) } }
        expectDenied { runBlocking { remindersForA().getReminder("a-med") } }
    }
}
