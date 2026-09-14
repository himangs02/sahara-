package com.yourteam.sahara

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.auth.AuthRepository
import com.yourteam.sahara.auth.PasswordHasher
import com.yourteam.sahara.auth.PatientAccessDeniedException
import com.yourteam.sahara.auth.RoomAuthDataSource
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Patient isolation against the real Room schema and SQL (caregiver links, session row, patient-scoped
 * queries), complementing the JVM tests that use in-memory fakes.
 */
@RunWith(AndroidJUnit4::class)
class PatientIsolationRoomTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var auth: AuthRepository
    private lateinit var patientA: String
    private lateinit var patientB: String

    // A small iteration count keeps the test fast; the algorithm and storage are unchanged.
    private fun newAuth() = AuthRepository(RoomAuthDataSource(db), PasswordHasher(iterations = 1_000))

    @Before fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        auth = newAuth()
        auth.register("caregiver@home", "correct horse battery", consent = true)
        patientA = auth.savePatient(null, "Patient A", 70, "Assam", "Assamese", consent = true).id
        patientB = auth.savePatient(null, "Patient B", 72, "Assam", "Hindi", consent = true).id

        auth.selectPatient(patientA)
        games(patientA).saveGameResult(result(patientA, 91f))
        reminders(patientA).insertReminder(Reminder(id = "a-walk", patientId = patientA, title = "A walk", minuteOfDay = 7 * 60))

        auth.selectPatient(patientB)
        games(patientB).saveGameResult(result(patientB, 42f))
        reminders(patientB).insertReminder(Reminder(id = "b-walk", patientId = patientB, title = "B walk", minuteOfDay = 9 * 60))
        Unit
    }

    @After fun tearDown() = db.close()

    private fun result(patientId: String, accuracy: Float) = GameResult(
        patientId = patientId, gameType = "MEMORY_MATCH", difficulty = Difficulty.EASY.name,
        totalPairs = 4, matchedPairs = 4, mistakes = 1, completionTimeSeconds = 30, accuracy = accuracy, completed = true
    )

    private fun games(patientId: String) = GameResultRepository(db.gameResultDao(), null, patientId, auth)
    private fun reminders(patientId: String) = ReminderRepository(db.reminderDao(), scopePatientId = patientId, auth = auth)

    private fun expectDenied(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("Expected patient access to be denied")
        } catch (_: PatientAccessDeniedException) {
        }
    }

    @Test fun eachPatientSeesOnlyTheirOwnRowsFromTheDatabase() = runBlocking {
        auth.selectPatient(patientA)
        assertEquals(listOf(91f), games(patientA).getAllGameResults().first().map { it.accuracy })
        assertEquals(listOf("a-walk"), reminders(patientA).getRemindersForPatient().first().map { it.id })

        auth.selectPatient(patientB)
        assertEquals(listOf(42f), games(patientB).getAllGameResults().first().map { it.accuracy })
        assertEquals(listOf("b-walk"), reminders(patientB).getRemindersForPatient().first().map { it.id })
    }

    @Test fun patientAScreensRevealNothingWhilePatientBIsSelected() = runBlocking {
        auth.selectPatient(patientB)
        assertTrue(games(patientA).getAllGameResults().first().isEmpty())
        assertTrue(reminders(patientA).getRemindersForPatient().first().isEmpty())
        assertNull(PatientRepository(db.patientDao(), null, auth).getPatientById(patientA).first())
    }

    @Test fun patientACannotReadOrChangePatientBRecords() = runBlocking {
        auth.selectPatient(patientA)
        expectDenied { reminders(patientA).getReminder("b-walk") }
        expectDenied { reminders(patientA).setCompleted("b-walk", true) }
        expectDenied { reminders(patientA).setEnabled("b-walk", false) }
        expectDenied { games(patientA).saveGameResult(result(patientB, 100f)) }

        // B's reminder is untouched in the database.
        val bWalk = db.reminderDao().getReminderById("b-walk")!!
        assertTrue(bWalk.enabled)
        assertEquals(Reminder.NOT_COMPLETED, bWalk.lastCompletedEpochDay)
    }

    @Test fun anotherCaregiverCannotSelectOrReadThesePatients() = runBlocking {
        auth.logout()
        auth.register("stranger@home", "another long password", consent = true)
        try {
            auth.selectPatient(patientA)
            fail("A caregiver must not select a patient they are not linked to")
        } catch (_: com.yourteam.sahara.auth.AuthException) {
        }
        assertTrue(auth.patients().first().isEmpty())
        assertTrue(games(patientA).getAllGameResults().first().isEmpty())
        expectDenied { reminders(patientA).getReminder("a-walk") }
    }

    @Test fun sessionIsStoredInTheDatabaseAndSurvivesARepositoryRestart() = runBlocking {
        auth.selectPatient(patientA)
        val restarted = newAuth()
        assertEquals(patientA, restarted.restoreSession()?.selectedPatientId)
        restarted.requirePatient(patientA)

        restarted.logout()
        assertNull(newAuth().restoreSession())
        assertTrue(games(patientA).getAllGameResults().first().isEmpty())
    }
}
