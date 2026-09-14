package com.yourteam.sahara.data.remote

import com.yourteam.sahara.api.BackendError
import com.yourteam.sahara.api.SaharaApiService
import com.yourteam.sahara.api.model.LoginRequestDto
import com.yourteam.sahara.api.model.RegisterRequestDto
import com.yourteam.sahara.api.model.TokenResponseDto
import com.yourteam.sahara.api.model.UserDto
import com.yourteam.sahara.api.toBackendError
import com.yourteam.sahara.auth.TokenStore

/** The Stage 3 vertical slice: takes the same email/password the caregiver just used to sign in
 * locally, authenticates against the real FastAPI backend, stores the returned JWT, and confirms
 * it works by calling the authenticated `/auth/me`. Deliberately never throws -- callers decide
 * how (or whether) to surface [Outcome.Failed] to the user; this class has no Android framework
 * dependency so it can be unit tested with a fake [SaharaApiService] and [TokenStore]. */
class BackendAuthService(
    private val api: SaharaApiService,
    private val tokenStore: TokenStore
) {
    sealed class Outcome {
        data class Verified(val user: UserDto) : Outcome()
        data class Failed(val error: BackendError) : Outcome()
    }

    suspend fun registerAndVerify(email: String, password: String): Outcome =
        authenticateAndVerify { api.register(RegisterRequestDto(email, password)) }

    suspend fun loginAndVerify(email: String, password: String): Outcome =
        authenticateAndVerify { api.login(LoginRequestDto(email, password)) }

    private suspend inline fun authenticateAndVerify(call: suspend () -> TokenResponseDto): Outcome {
        return try {
            val token = call()
            tokenStore.saveToken(token.accessToken)
            Outcome.Verified(api.me())
        } catch (t: Throwable) {
            Outcome.Failed(t.toBackendError())
        }
    }
}
