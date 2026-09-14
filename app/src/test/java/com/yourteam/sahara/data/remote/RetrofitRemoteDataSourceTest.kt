package com.yourteam.sahara.data.remote

import com.yourteam.sahara.api.Iso8601
import com.yourteam.sahara.api.SaharaApiService
import com.yourteam.sahara.api.model.GameResultDto
import com.yourteam.sahara.api.model.GameResultUpsertDto
import com.yourteam.sahara.api.model.LoginRequestDto
import com.yourteam.sahara.api.model.PatientCreateDto
import com.yourteam.sahara.api.model.PatientDto
import com.yourteam.sahara.api.model.PatientUpdateDto
import com.yourteam.sahara.api.model.RegisterRequestDto
import com.yourteam.sahara.api.model.ReminderDto
import com.yourteam.sahara.api.model.ReminderUpsertDto
import com.yourteam.sahara.api.model.TokenResponseDto
import com.yourteam.sahara.api.model.UserDto
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class FakeApi(
    private val patients: List<PatientDto> = emptyList(),
    private val results: List<GameResultDto> = emptyList(),
    private val reminders: List<ReminderDto> = emptyList(),
    private val createPatientResult: Result<PatientDto> = Result.failure(IllegalStateException("not stubbed")),
    private val uploadResultResult: Result<GameResultDto> = Result.failure(IllegalStateException("not stubbed")),
    private val uploadReminderResult: Result<ReminderDto> = Result.failure(IllegalStateException("not stubbed")),
    private val deleteReminderResult: Result<Unit> = Result.success(Unit)
) : SaharaApiService {
    var lastCreatePatientBody: PatientCreateDto? = null
    var lastUploadPatientId: String? = null
    var lastUploadBody: GameResultUpsertDto? = null
    var lastReminderUploadPatientId: String? = null
    var lastReminderUploadBody: ReminderUpsertDto? = null
    var lastDeletedReminderId: String? = null

    override suspend fun register(body: RegisterRequestDto): TokenResponseDto = throw UnsupportedOperationException()
    override suspend fun login(body: LoginRequestDto): TokenResponseDto = throw UnsupportedOperationException()
    override suspend fun me(): UserDto = throw UnsupportedOperationException()
    override suspend fun getPatients(): List<PatientDto> = patients
    override suspend fun getPatient(patientId: String): PatientDto = throw UnsupportedOperationException()

    override suspend fun createPatient(body: PatientCreateDto): PatientDto {
        lastCreatePatientBody = body
        return createPatientResult.getOrThrow()
    }

    override suspend fun updatePatient(patientId: String, body: PatientUpdateDto): PatientDto =
        throw UnsupportedOperationException()

    override suspend fun uploadGameResult(patientId: String, body: GameResultUpsertDto): GameResultDto {
        lastUploadPatientId = patientId
        lastUploadBody = body
        return uploadResultResult.getOrThrow()
    }

    override suspend fun getGameResults(patientId: String): List<GameResultDto> = results

    override suspend fun uploadReminder(patientId: String, body: ReminderUpsertDto): ReminderDto {
        lastReminderUploadPatientId = patientId
        lastReminderUploadBody = body
        return uploadReminderResult.getOrThrow()
    }

    override suspend fun getReminders(patientId: String): List<ReminderDto> = reminders

    override suspend fun deleteReminder(patientId: String, reminderId: String) {
        lastDeletedReminderId = reminderId
        deleteReminderResult.getOrThrow()
    }
}

private fun notFoundException() = HttpException(Response.error<Unit>(404, "".toResponseBody("text/plain".toMediaType())))

class RetrofitRemoteDataSourceTest {

    private val demoPatientDto = PatientDto(
        id = "p1", name = "Kamala Devi", age = 74, preferredLanguage = "Assamese", region = "Assam",
        createdAt = "2026-09-14T00:00:00Z", updatedAt = "2026-09-14T00:00:00Z"
    )

    @Test fun `uploadPatient posts the mapped dto and returns true on success`() = runTest {
        val api = FakeApi(createPatientResult = Result.success(demoPatientDto))
        val source = RetrofitRemoteDataSource(api)
        val patient = Patient(syncId = "p1", id = "p1", name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese")

        val success = source.uploadPatient(patient)

        assertTrue(success)
        assertEquals("p1", api.lastCreatePatientBody?.id)
    }

    @Test fun `uploadPatient returns false, never throws, on backend failure`() = runTest {
        val api = FakeApi(createPatientResult = Result.failure(RuntimeException("boom")))
        val source = RetrofitRemoteDataSource(api)
        val patient = Patient(syncId = "p1", id = "p1", name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese")

        val success = source.uploadPatient(patient)

        assertFalse(success)
    }

    @Test fun `uploadGameResult posts to the correct patient-scoped path with the mapped body`() = runTest {
        val resultDto = GameResultDto(
            id = "r1", patientId = "p1", gameType = "MEMORY_MATCH", difficulty = "EASY",
            totalPairs = 6, matchedPairs = 6, mistakes = 0, completionTimeSeconds = 30, accuracy = 100f,
            completed = true, occurredAt = "2026-09-14T00:00:00Z", syncedAt = "2026-09-14T00:00:00Z"
        )
        val api = FakeApi(uploadResultResult = Result.success(resultDto))
        val source = RetrofitRemoteDataSource(api)
        val result = GameResult(
            patientId = "p1", syncId = "r1", difficulty = "EASY", totalPairs = 6, matchedPairs = 6,
            mistakes = 0, completionTimeSeconds = 30, accuracy = 100f, completed = true, timestamp = Iso8601.toMillis("2026-09-14T00:00:00Z")
        )

        val success = source.uploadGameResult(result)

        assertTrue(success)
        assertEquals("p1", api.lastUploadPatientId)
        assertEquals("r1", api.lastUploadBody?.id)
    }

    @Test fun `fetchPatients maps every backend patient to a domain Patient`() = runTest {
        val api = FakeApi(patients = listOf(demoPatientDto))
        val source = RetrofitRemoteDataSource(api)

        val patients = source.fetchPatients()

        assertEquals(1, patients.size)
        assertEquals("p1", patients.first().id)
        assertEquals("Assamese", patients.first().language)
    }

    @Test fun `fetchGameResultsForPatient maps every backend result to a domain GameResult`() = runTest {
        val resultDto = GameResultDto(
            id = "r1", patientId = "p1", gameType = "MEMORY_MATCH", difficulty = "EASY",
            totalPairs = 6, matchedPairs = 6, mistakes = 0, completionTimeSeconds = 30, accuracy = 100f,
            completed = true, occurredAt = "2026-09-14T00:00:00Z", syncedAt = "2026-09-14T00:00:00Z"
        )
        val api = FakeApi(results = listOf(resultDto))
        val source = RetrofitRemoteDataSource(api)

        val results = source.fetchGameResultsForPatient("p1")

        assertEquals(1, results.size)
        assertEquals("r1", results.first().syncId)
        assertEquals("p1", results.first().patientId)
    }

    @Test fun `uploadReminder posts to the correct patient-scoped path with the mapped body`() = runTest {
        val reminderDto = ReminderDto(
            id = "rem1", patientId = "p1", title = "Morning Medicine", description = "", reminderType = "MEDICINE",
            minuteOfDay = 480, enabled = true, createdAt = "2026-09-14T00:00:00Z", updatedAt = "2026-09-14T00:00:00Z"
        )
        val api = FakeApi(uploadReminderResult = Result.success(reminderDto))
        val source = RetrofitRemoteDataSource(api)
        val reminder = Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine", minuteOfDay = 480)

        val success = source.uploadReminder("p1", reminder)

        assertTrue(success)
        assertEquals("p1", api.lastReminderUploadPatientId)
        assertEquals("rem1", api.lastReminderUploadBody?.id)
    }

    @Test fun `uploadReminder returns false, never throws, on backend failure`() = runTest {
        val api = FakeApi(uploadReminderResult = Result.failure(RuntimeException("boom")))
        val source = RetrofitRemoteDataSource(api)
        val reminder = Reminder(id = "rem1", patientId = "p1", title = "Morning Medicine", minuteOfDay = 480)

        val success = source.uploadReminder("p1", reminder)

        assertFalse(success)
    }

    @Test fun `deleteReminder returns true on success`() = runTest {
        val api = FakeApi()
        val source = RetrofitRemoteDataSource(api)

        val success = source.deleteReminder("p1", "rem1")

        assertTrue(success)
        assertEquals("rem1", api.lastDeletedReminderId)
    }

    @Test fun `deleteReminder treats a 404 as success (already deleted)`() = runTest {
        val api = FakeApi(deleteReminderResult = Result.failure(notFoundException()))
        val source = RetrofitRemoteDataSource(api)

        val success = source.deleteReminder("p1", "rem1")

        assertTrue(success)
    }

    @Test fun `deleteReminder returns false on a real failure`() = runTest {
        val api = FakeApi(deleteReminderResult = Result.failure(RuntimeException("network down")))
        val source = RetrofitRemoteDataSource(api)

        val success = source.deleteReminder("p1", "rem1")

        assertFalse(success)
    }

    @Test fun `fetchRemindersForPatient maps every backend reminder to a domain Reminder`() = runTest {
        val reminderDto = ReminderDto(
            id = "rem1", patientId = "p1", title = "Morning Medicine", description = "", reminderType = "MEDICINE",
            minuteOfDay = 480, enabled = true, createdAt = "2026-09-14T00:00:00Z", updatedAt = "2026-09-14T00:00:00Z"
        )
        val api = FakeApi(reminders = listOf(reminderDto))
        val source = RetrofitRemoteDataSource(api)

        val reminders = source.fetchRemindersForPatient("p1")

        assertEquals(1, reminders.size)
        assertEquals("rem1", reminders.first().id)
        assertEquals("p1", reminders.first().patientId)
        assertEquals(480, reminders.first().minuteOfDay)
    }
}
