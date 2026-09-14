package com.yourteam.sahara.backend

import com.yourteam.sahara.api.BackendError
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
import com.yourteam.sahara.auth.TokenStore
import com.yourteam.sahara.data.remote.BackendAuthService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

private class FakeTokenStore : TokenStore {
    var saved: String? = null
        private set

    override fun saveToken(token: String) {
        saved = token
    }

    override fun getToken(): String? = saved

    override fun clearToken() {
        saved = null
    }
}

/** A hand-written fake rather than a mocking library: this project has no mocking dependency, and
 * the interface is small enough that a fake reads more plainly than a mock setup would. */
private class FakeApiService(
    private val loginResult: Result<TokenResponseDto> = Result.failure(IllegalStateException("not stubbed")),
    private val registerResult: Result<TokenResponseDto> = Result.failure(IllegalStateException("not stubbed")),
    private val meResult: Result<UserDto> = Result.failure(IllegalStateException("not stubbed"))
) : SaharaApiService {
    var meCallCount = 0
        private set

    override suspend fun register(body: RegisterRequestDto): TokenResponseDto = registerResult.getOrThrow()

    override suspend fun login(body: LoginRequestDto): TokenResponseDto = loginResult.getOrThrow()

    override suspend fun me(): UserDto {
        meCallCount++
        return meResult.getOrThrow()
    }

    override suspend fun getPatients(): List<PatientDto> = emptyList()
    override suspend fun getPatient(patientId: String): PatientDto = throw UnsupportedOperationException()
    override suspend fun createPatient(body: PatientCreateDto): PatientDto = throw UnsupportedOperationException()
    override suspend fun updatePatient(patientId: String, body: PatientUpdateDto): PatientDto =
        throw UnsupportedOperationException()
    override suspend fun uploadGameResult(patientId: String, body: GameResultUpsertDto): GameResultDto =
        throw UnsupportedOperationException()
    override suspend fun getGameResults(patientId: String): List<GameResultDto> = emptyList()
    override suspend fun uploadReminder(patientId: String, body: ReminderUpsertDto): ReminderDto =
        throw UnsupportedOperationException()
    override suspend fun getReminders(patientId: String): List<ReminderDto> = emptyList()
    override suspend fun deleteReminder(patientId: String, reminderId: String) = Unit
}

private val demoUser = UserDto(id = "caregiver-1", email = "demo@sahara.local", isActive = true, createdAt = "2026-09-14T00:00:00Z")
private val demoToken = TokenResponseDto(accessToken = "jwt-token-value", user = demoUser)

class BackendAuthServiceTest {

    @Test fun `successful login stores the token and returns the verified caregiver`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeApiService(
            loginResult = Result.success(demoToken),
            meResult = Result.success(demoUser)
        )
        val service = BackendAuthService(api, tokenStore)

        val outcome = service.loginAndVerify("demo@sahara.local", "correct-password")

        assertTrue(outcome is BackendAuthService.Outcome.Verified)
        assertEquals(demoUser, (outcome as BackendAuthService.Outcome.Verified).user)
        assertEquals("jwt-token-value", tokenStore.getToken())
        assertEquals(1, api.meCallCount)
    }

    @Test fun `successful registration stores the token and returns the verified caregiver`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeApiService(
            registerResult = Result.success(demoToken),
            meResult = Result.success(demoUser)
        )
        val service = BackendAuthService(api, tokenStore)

        val outcome = service.registerAndVerify("demo@sahara.local", "correct-password")

        assertTrue(outcome is BackendAuthService.Outcome.Verified)
        assertEquals("jwt-token-value", tokenStore.getToken())
    }

    @Test fun `a login failure never stores a token and maps to a BackendError`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeApiService(loginResult = Result.failure(UnknownHostException()))
        val service = BackendAuthService(api, tokenStore)

        val outcome = service.loginAndVerify("demo@sahara.local", "wrong")

        assertTrue(outcome is BackendAuthService.Outcome.Failed)
        assertEquals(BackendError.NetworkUnavailable, (outcome as BackendAuthService.Outcome.Failed).error)
        assertNull(tokenStore.getToken())
        assertEquals(0, api.meCallCount)
    }

    @Test fun `a failure calling auth me after a successful login is still reported as Failed`() = runTest {
        val tokenStore = FakeTokenStore()
        val api = FakeApiService(
            loginResult = Result.success(demoToken),
            meResult = Result.failure(UnknownHostException())
        )
        val service = BackendAuthService(api, tokenStore)

        val outcome = service.loginAndVerify("demo@sahara.local", "correct-password")

        // The token was still saved from the successful login step -- only the /auth/me
        // confirmation step failed. This matches BackendAuthService's actual behavior: it saves
        // before verifying, so a transient /auth/me failure doesn't discard an otherwise-valid token.
        assertEquals("jwt-token-value", tokenStore.getToken())
        assertTrue(outcome is BackendAuthService.Outcome.Failed)
    }
}
